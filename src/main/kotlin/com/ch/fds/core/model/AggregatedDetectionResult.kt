package com.ch.fds.core.model

import java.time.Instant
import java.util.UUID

data class AggregatedDetectionResult(
    val userId: String,
    val activityId: UUID,
    val activityType: String,
    val totalRiskScore: RiskScore,
    val detectionResults: List<DetectionResult>,
    val decision: FraudDecision,
    val analyzedAt: Instant = Instant.now(),
    /**
     * AI 모델 예측 점수 (0.0 ~ 1.0).
     * null이면 AI 모델이 사용되지 않았음을 의미.
     */
    val aiScore: Float? = null
) {

    val fraudDetections: List<DetectionResult>
        get() = detectionResults.filter {
            it.detected
        }
    
    val maxRiskScore: RiskScore
        get() = detectionResults.maxOfOrNull { it.riskScore } ?: RiskScore.ZERO
    
    /**
     * AI 점수 포함 여부
     */
    val hasAiScore: Boolean
        get() = aiScore != null
    
    /**
     * AI가 고위험으로 판단했는지 여부 (85% 이상)
     */
    val isAiHighRisk: Boolean
        get() = aiScore?.let { it >= 0.85f } ?: false
}
