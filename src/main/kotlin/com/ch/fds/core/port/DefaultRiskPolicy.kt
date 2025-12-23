package com.ch.fds.core.port

import com.ch.fds.core.model.DetectionResult
import com.ch.fds.core.model.FraudDecision
import com.ch.fds.core.model.RiskScore

class DefaultRiskPolicy : RiskPolicy {
    override val policyName: String = "DefaultRiskPolicy"
    
    companion object {
        private const val REVIEW_THRESHOLD = 31
        private const val BLOCK_THRESHOLD = 61
    }
    
    override fun decide(
        results: List<DetectionResult>
    ): FraudDecision {
        if (results.isEmpty()) {
            return FraudDecision.APPROVE
        }
        
        val totalScore = aggregateScores(results.map { it.riskScore })
        
        return when {
            totalScore.value >= BLOCK_THRESHOLD -> FraudDecision.BLOCK
            totalScore.value >= REVIEW_THRESHOLD -> FraudDecision.REVIEW
            else -> FraudDecision.APPROVE
        }
    }
    
    override fun aggregateScores(
        scores: List<RiskScore>
    ): RiskScore {
        if (scores.isEmpty()) {
            return RiskScore.ZERO
        }
        
        val maxScore = scores.maxOf { it.value }
        
        // TODO: 가중 평균 방식으로 변경 필요
        // val avgScore = scores.map { it.value }.average().toInt()
        
        return RiskScore(
            value = maxScore
        )
    }
}