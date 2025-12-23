package com.ch.fds.core.model

import java.time.Instant

data class DetectionResult(
    val ruleId: String,
    val ruleName: String,
    val riskScore: RiskScore,
    val detected: Boolean,
    val reason: String,
    val metadata: Map<String, Any> = emptyMap(),
    val detectedAt: Instant = Instant.now()
) {
    companion object {

        fun normal(
            ruleId: String,
            ruleName: String,
            reason: String = "No suspicious activity detected"
        ): DetectionResult {
            return DetectionResult(
                ruleId = ruleId,
                ruleName = ruleName,
                riskScore = RiskScore.ZERO,
                detected = false,
                reason = reason
            )
        }
        
        fun fraud(
            ruleId: String, 
            ruleName: String, 
            riskScore: RiskScore, 
            reason: String,
            metadata: Map<String, Any> = emptyMap()
        ): DetectionResult {
            return DetectionResult(
                ruleId = ruleId,
                ruleName = ruleName,
                riskScore = riskScore,
                detected = true,
                reason = reason,
                metadata = metadata
            )
        }
    }
}

