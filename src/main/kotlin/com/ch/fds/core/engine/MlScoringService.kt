package com.ch.fds.core.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.ch.fds.core.model.TransactionFeatures
import com.ch.fds.core.port.ModelInferencePort
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicLong

@Service
class MlScoringService : ModelInferencePort {
    
    private val logger = LoggerFactory.getLogger(MlScoringService::class.java)
    
    companion object {
        private const val MODEL_RESOURCE_PATH = "models/fraud_detection_v1.onnx"
        private const val MODEL_NAME = "fraud_detection_v1"
        private const val MODEL_VERSION = "1.0.0"
        
        // ONNX 입출력 노드 이름
        private const val INPUT_NODE_NAME = "input"
        private const val OUTPUT_NODE_NAME = "output"
        
        // Heuristic Fallback 임계값
        private const val HIGH_RISK_AMOUNT_THRESHOLD = 1_000_000.0 // 100만원
        private const val HIGH_RISK_SCORE_THRESHOLD = 70
        private const val RAPID_ACTIVITY_THRESHOLD = 10
    }
    
    override val modelName: String = MODEL_NAME
    override val modelVersion: String = MODEL_VERSION
    
    @Volatile
    override var isModelLoaded: Boolean = false
    
    private var ortEnvironment: OrtEnvironment? = null

    private var ortSession: OrtSession? = null
    
    private val inferenceCount = AtomicLong(0)
    private val fallbackCount = AtomicLong(0)
    
    /**
     * 서비스 초기화
     */
    @PostConstruct
    fun initialize() {
        try {
            loadModel()
        } catch (e: Exception) {
            logger.warn("⚠️ Failed to load ONNX model, will use heuristic fallback: {}", e.message)
            isModelLoaded = false
        }
    }
    
    /**
     * 서비스 종료
     */
    @PreDestroy
    fun shutdown() {
        try {
            ortSession?.close()
            ortEnvironment?.close()
            logger.info("✅ ONNX Runtime resources released")
        } catch (e: Exception) {
            logger.error("❌ Error releasing ONNX Runtime resources", e)
        }
    }
    
    /**
     * 모델 로드
     */
    private fun loadModel() {
        logger.info("🔄 Loading ONNX model: {}", MODEL_RESOURCE_PATH)
        
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()
            
            // 모델 파일 읽기
            val modelResource = ClassPathResource(MODEL_RESOURCE_PATH)
            
            if (!modelResource.exists()) {
                throw IllegalStateException("Model file not found: $MODEL_RESOURCE_PATH")
            }
            
            val modelBytes = modelResource.inputStream.readBytes()
            
            // 세션 옵션 설정
            val sessionOptions = OrtSession.SessionOptions().apply {
                // CPU 최적화 활성화
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                
                // 인터-오퍼레이터 병렬성 (CPU 스레드 수)
                setInterOpNumThreads(2)
                setIntraOpNumThreads(2)
            }
            
            // 세션 생성
            ortSession = ortEnvironment!!.createSession(modelBytes, sessionOptions)
            
            isModelLoaded = true
            
            logger.info(
                "✅ ONNX model loaded successfully: modelName={}, version={}, inputNames={}, outputNames={}",
                modelName,
                modelVersion,
                ortSession!!.inputNames,
                ortSession!!.outputNames
            )
            
        } catch (e: Exception) {
            logger.error("❌ Failed to load ONNX model", e)
            isModelLoaded = false
            throw e
        }
    }
    
    /**
     * 사기 확률 예측 (0.0 ~ 1.0)
     */
    override suspend fun predictScore(
        features: TransactionFeatures
    ): Float = withContext(Dispatchers.Default) {
        inferenceCount.incrementAndGet()
        
        try {
            if (isModelLoaded && ortSession != null) {
                // ONNX 모델 추론
                predictWithOnnx(features)
            } else {
                // Fallback: 휴리스틱 기반 점수
                fallbackCount.incrementAndGet()
                predictWithHeuristic(features)
            }
        } catch (e: Exception) {
            logger.error("❌ Error during ML inference, falling back to heuristic", e)
            fallbackCount.incrementAndGet()
            predictWithHeuristic(features)
        }
    }
    
    /**
     * ONNX Runtime을 사용한 실제 추론
     */
    private fun predictWithOnnx(
        features: TransactionFeatures
    ): Float {
        val session = ortSession ?: throw IllegalStateException("ONNX session not initialized")
        val env = ortEnvironment ?: throw IllegalStateException("ONNX environment not initialized")
        
        val inputArray = features.toFloatArray()
        
        // ONNX 텐서 생성
        // Shape: [1, FEATURE_VECTOR_SIZE] (배치 크기 1)
        val shape = longArrayOf(1, TransactionFeatures.FEATURE_VECTOR_SIZE.toLong())
        val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputArray), shape)
        
        try {
            // 추론 실행
            val inputs = mapOf(INPUT_NODE_NAME to inputTensor)
            val results = session.run(inputs)
            
            try {
                // 출력 텐서 추출
                val outputTensor = results.get(0) as OnnxTensor
                val outputBuffer = outputTensor.floatBuffer
                
                // 첫 번째 값이 사기 확률
                val fraudScore = outputBuffer.get(0)
                
                // 0.0 ~ 1.0 범위로 클램핑
                return fraudScore.coerceIn(0.0f, 1.0f)
                
            } finally {
                results.close()
            }
            
        } finally {
            inputTensor.close()
        }
    }
    
    /**
     * 휴리스틱 기반 Fallback 예측.
     * 
     * 실제 모델이 없을 때 규칙 기반으로 사기 확률을 계산.
     * 
     * 1. 금액이 100만원 이상이고 리스크 점수가 70 이상 → 0.9 (High Risk)
     * 2. 최근 1분 내 활동이 10회 이상 → 0.85 (Rapid Activity)
     * 3. 현재 리스크 점수가 85 이상 (CRITICAL) → 0.95 (Critical Risk)
     * 4. 현재 리스크 점수가 70 이상 (HIGH) → 0.75 (High Risk)
     * 5. 현재 리스크 점수가 50 이상 (MEDIUM) → 0.55 (Medium Risk)
     * 6. 그 외 → 리스크 점수 / 100.0
     */
    private fun predictWithHeuristic(
        features: TransactionFeatures
    ): Float {
        logger.debug("🔧 Using heuristic fallback for prediction: userId={}", features.userId)
        
        var score = features.currentRiskScore / 100.0f
        
        // 규칙 1: 고액 거래 + 높은 리스크 점수
        if (features.amount >= HIGH_RISK_AMOUNT_THRESHOLD && 
            features.currentRiskScore >= HIGH_RISK_SCORE_THRESHOLD) {
            score = maxOf(score, 0.9f)
        }
        
        // 규칙 2: 빠른 연속 활동
        if (features.activityCountLast1Min >= RAPID_ACTIVITY_THRESHOLD) {
            score = maxOf(score, 0.85f)
        }
        
        // 규칙 3: CRITICAL 리스크 점수 (85+)
        if (features.currentRiskScore >= 85) {
            score = maxOf(score, 0.95f)
        }
        
        // 규칙 4: HIGH 리스크 점수 (70+)
        if (features.currentRiskScore >= 70) {
            score = maxOf(score, 0.75f)
        }
        
        // 규칙 5: MEDIUM 리스크 점수 (50+)
        if (features.currentRiskScore >= 50) {
            score = maxOf(score, 0.55f)
        }
        
        return score.coerceIn(0.0f, 1.0f)
    }
    
    /**
     * 모델 재로드
     */
    override suspend fun reloadModel(
        modelPath: String
    ) = withContext(Dispatchers.IO) {
        logger.info("🔄 Reloading model from: {}", modelPath)
        
        try {
            // 기존 세션 종료
            ortSession?.close()
            
            // 새 모델 로드
            loadModel()
            
            logger.info("✅ Model reloaded successfully")
            
        } catch (e: Exception) {
            logger.error("❌ Failed to reload model", e)
            isModelLoaded = false
            throw e
        }
    }
    
    /**
     * 추론 통계 조회
     */
    fun getStats(): InferenceStats {
        return InferenceStats(
            totalInferences = inferenceCount.get(),
            fallbackInferences = fallbackCount.get(),
            modelLoaded = isModelLoaded,
            modelName = modelName,
            modelVersion = modelVersion
        )
    }
    
    /**
     * 통계 초기화
     */
    fun resetStats() {
        inferenceCount.set(0)
        fallbackCount.set(0)
        logger.info("📊 Inference stats reset")
    }
}

/**
 * 추론 통계 정보
 */
data class InferenceStats(
    val totalInferences: Long,
    val fallbackInferences: Long,
    val modelLoaded: Boolean,
    val modelName: String,
    val modelVersion: String
) {
    val modelInferenceRate: Double
        get() = if (totalInferences > 0) {
            ((totalInferences - fallbackInferences).toDouble() / totalInferences) * 100
        } else {
            0.0
        }
    
    override fun toString(): String {
        return """
            InferenceStats(
              totalInferences=$totalInferences,
              fallbackInferences=$fallbackInferences,
              modelInferenceRate=${String.format("%.2f", modelInferenceRate)}%,
              modelLoaded=$modelLoaded,
              modelName=$modelName,
              modelVersion=$modelVersion
            )
        """.trimIndent()
    }
}

