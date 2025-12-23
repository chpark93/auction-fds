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
    val analyzedAt: Instant = Instant.now()
) {

    val fraudDetections: List<DetectionResult>
        get() = detectionResults.filter {
            it.detected
        }
    
    val maxRiskScore: RiskScore
        get() = detectionResults.maxOfOrNull { it.riskScore } ?: RiskScore.ZERO
}
