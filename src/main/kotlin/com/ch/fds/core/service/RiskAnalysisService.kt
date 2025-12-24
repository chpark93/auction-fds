package com.ch.fds.core.service

import com.ch.fds.core.engine.FeatureExtractor
import com.ch.fds.core.model.*
import com.ch.fds.core.port.DetectionRule
import com.ch.fds.core.port.ModelInferencePort
import com.ch.fds.core.port.RiskPolicy
import com.ch.fds.infrastructure.service.FastBlacklistService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

class RiskAnalysisService(
    private val rules: List<DetectionRule>,
    private val riskPolicy: RiskPolicy,
    private val blacklistService: FastBlacklistService?,
    private val mlScoringService: ModelInferencePort?
) {
    private val logger = LoggerFactory.getLogger(RiskAnalysisService::class.java)
    
    companion object {
        // AI 모델 점수 임계값
        // 85% 이상이면 사기로 판단
        private const val AI_HIGH_RISK_THRESHOLD = 0.85f
        // 60% 이상이면 주의
        private const val AI_MEDIUM_RISK_THRESHOLD = 0.60f
    }
    
    suspend fun analyze(
        activity: UserActivity,
        profile: UserRiskProfile
    ): AggregatedDetectionResult {
        logger.debug("🔍 [HYBRID] Analyzing activity: type={}, userId={}", activity.activityType, activity.userId)
        
        // Bloom filter logic
        val isBlacklisted = checkBlacklist(activity.userId)
        if (isBlacklisted) {
            logger.warn("🚫 [BLACKLIST] User is blacklisted: userId={}", activity.userId)
            
            return AggregatedDetectionResult(
                userId = activity.userId,
                activityId = activity.activityId,
                activityType = activity.activityType,
                totalRiskScore = RiskScore.CRITICAL,
                detectionResults = listOf(
                    DetectionResult.fraud(
                        ruleId = "BLACKLIST_CHECK",
                        ruleName = "Blacklist Detection",
                        riskScore = RiskScore.CRITICAL,
                        reason = "User is in the blacklist",
                        metadata = emptyMap()
                    )
                ),
                decision = FraudDecision.BLOCK,
                aiScore = null
            )
        }
        
        // Rule based detection
        val applicableRules = rules.filter { 
            it.enabled && it.supports(activityType = activity.activityType)
        }
        
        logger.debug("📋 [RULES] Found {} applicable rules for activity type: {}", applicableRules.size, activity.activityType)
        
        val ruleDetectionResults = if (applicableRules.isNotEmpty()) {
            evaluateRules(
                rules = applicableRules,
                activity = activity,
                profile = profile
            )
        } else {
            emptyList()
        }
        
        // AI based detection
        val aiScore = evaluateAiModel(activity, profile)
        
        val finalResult = aggregateHybridResults(
            activity = activity,
            ruleResults = ruleDetectionResults,
            aiScore = aiScore
        )
        
        logger.info(
            "✅ [HYBRID] Analysis complete: userId={}, decision={}, ruleRisk={}, aiScore={}",
            activity.userId,
            finalResult.decision,
            finalResult.totalRiskScore.value,
            aiScore?.let { String.format("%.3f", it) } ?: "N/A"
        )
        
        return finalResult
    }
    
    /**
     * Bloom Filter로 블랙리스트 검사
     */
    private fun checkBlacklist(
        userId: String
    ): Boolean {
        if (blacklistService == null) {
            return false
        }
        
        val startTime = System.nanoTime()
        val result = blacklistService.isMightContain(userId)
        val elapsedNanos = System.nanoTime() - startTime
        
        logger.debug(
            "⚡ [BLOOM] Blacklist check: userId={}, result={}, time={}ns",
            userId,
            result,
            elapsedNanos
        )
        
        // TODO: Bloom Filter가 true를 반환하면, 실제 DB/Redis 체크 프로세스 필요
        // TODO: DB 확인 로직 추가

        return result
    }
    
    /**
     * AI 모델로 사기 확률 예측
     */
    private suspend fun evaluateAiModel(
        activity: UserActivity,
        profile: UserRiskProfile
    ): Float? {
        if (mlScoringService == null) {
            return null
        }
        
        return try {
            val startTime = System.nanoTime()
            
            // Feature 추출
            val features = FeatureExtractor.extract(
                activity = activity,
                profile = profile
            )
            
            // 추론
            val score = mlScoringService.predictScore(
                features = features
            )
            
            val elapsedMillis = (System.nanoTime() - startTime) / 1_000_000.0
            
            logger.debug(
                "🤖 [AI] Model prediction: userId={}, score={}, time={}ms",
                activity.userId,
                String.format("%.3f", score),
                String.format("%.2f", elapsedMillis)
            )
            
            score
            
        } catch (e: Exception) {
            logger.error("❌ [AI] Error during ML inference: userId=${activity.userId}", e)
            null
        }
    }
    
    /**
     * Hybrid 결과 집계 및 최종 결정.
     * - Rule이 BLOCK 트리거 OR AI Score > 0.85 → BLOCK
     * - Rule이 REVIEW 트리거 OR AI Score > 0.60 → REVIEW
     * - 그 외 → APPROVE
     */
    private fun aggregateHybridResults(
        activity: UserActivity,
        ruleResults: List<DetectionResult>,
        aiScore: Float?
    ): AggregatedDetectionResult {

        // Rule 기반 결정
        val ruleTotalScore = if (ruleResults.isNotEmpty()) {
            riskPolicy.aggregateScores(ruleResults.map { it.riskScore })
        } else {
            RiskScore.ZERO
        }
        
        val ruleDecision = if (ruleResults.isNotEmpty()) {
            riskPolicy.decide(ruleResults)
        } else {
            FraudDecision.APPROVE
        }
        
        // AI 기반 결정
        val aiDecision = when {
            aiScore == null -> FraudDecision.APPROVE
            aiScore >= AI_HIGH_RISK_THRESHOLD -> FraudDecision.BLOCK
            aiScore >= AI_MEDIUM_RISK_THRESHOLD -> FraudDecision.REVIEW
            else -> FraudDecision.APPROVE
        }
        
        // 최종 결정: Rule OR AI (더 엄격한 쪽 선택)
        val finalDecision = when {
            ruleDecision == FraudDecision.BLOCK || aiDecision == FraudDecision.BLOCK -> FraudDecision.BLOCK
            ruleDecision == FraudDecision.REVIEW || aiDecision == FraudDecision.REVIEW -> FraudDecision.REVIEW
            else -> FraudDecision.APPROVE
        }
        
        // AI 점수 RiskScore로 변환
        val aiRiskScore = aiScore?.let { RiskScore((it * 100).toInt()) }
        
        // 최종 리스크 점수: Rule과 AI 중 더 높은 값
        val finalRiskScore = if (aiRiskScore != null) {
            maxOf(ruleTotalScore, aiRiskScore)
        } else {
            ruleTotalScore
        }
        
        // AI 결과 -> DetectionResult 추가
        val allResults = ruleResults.toMutableList()
        if (aiScore != null && aiScore >= AI_MEDIUM_RISK_THRESHOLD) {
            allResults.add(
                DetectionResult.fraud(
                    ruleId = "AI_MODEL_INFERENCE",
                    ruleName = "ML-based Fraud Detection",
                    riskScore = aiRiskScore ?: RiskScore.ZERO,
                    reason = "AI model detected high fraud probability: ${String.format("%.2f", aiScore * 100)}%",
                    metadata = mapOf(
                        "aiScore" to aiScore,
                        "threshold" to AI_HIGH_RISK_THRESHOLD
                    )
                )
            )
        }
        
        return AggregatedDetectionResult(
            userId = activity.userId,
            activityId = activity.activityId,
            activityType = activity.activityType,
            totalRiskScore = finalRiskScore,
            detectionResults = allResults,
            decision = finalDecision,
            aiScore = aiScore
        )
    }
    
    private suspend fun evaluateRules(
        rules: List<DetectionRule>,
        activity: UserActivity,
        profile: UserRiskProfile
    ): List<DetectionResult> = coroutineScope {
        rules.map { rule ->
            async {
                try {
                    logger.debug("Evaluating rule: {}", rule.ruleName)

                    rule.evaluate(
                        activity = activity,
                        profile = profile
                    )
                } catch (e: Exception) {
                    logger.error("Error evaluating rule: ${rule.ruleName}", e)

                    DetectionResult.normal(
                        ruleId = rule.ruleId,
                        ruleName = rule.ruleName,
                        reason = "Rule evaluation failed: ${e.message}"
                    )
                }
            }
        }.awaitAll()
    }
    
    private fun createEmptyResult(
        activity: UserActivity
    ): AggregatedDetectionResult {
        return AggregatedDetectionResult(
            userId = activity.userId,
            activityId = activity.activityId,
            activityType = activity.activityType,
            totalRiskScore = RiskScore.ZERO,
            detectionResults = emptyList(),
            decision = FraudDecision.APPROVE
        )
    }
}

