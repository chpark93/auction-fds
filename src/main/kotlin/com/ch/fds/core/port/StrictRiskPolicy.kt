package com.ch.fds.core.port

import com.ch.fds.core.model.DetectionResult
import com.ch.fds.core.model.FraudDecision
import com.ch.fds.core.model.RiskScore

class StrictRiskPolicy : RiskPolicy {
    override val policyName: String = "StrictRiskPolicy"
    
    override fun decide(
        results: List<DetectionResult>
    ): FraudDecision {
        val hasAnyFraud = results.any { it.detected }
        
        return if (hasAnyFraud) {
            FraudDecision.BLOCK
        } else {
            FraudDecision.APPROVE
        }
    }
    
    override fun aggregateScores(
        scores: List<RiskScore>
    ): RiskScore {
        if (scores.isEmpty()) {
            return RiskScore.ZERO
        }
        
        val totalValue = scores.sumOf { it.value }.coerceIn(0, 100)

        return RiskScore(
            value = totalValue
        )
    }
}