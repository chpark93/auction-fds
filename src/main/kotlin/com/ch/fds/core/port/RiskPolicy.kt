package com.ch.fds.core.port

import com.ch.fds.core.model.DetectionResult
import com.ch.fds.core.model.FraudDecision
import com.ch.fds.core.model.RiskScore

interface RiskPolicy {
    val policyName: String
    
    fun decide(
        results: List<DetectionResult>
    ): FraudDecision
    
    fun aggregateScores(
        scores: List<RiskScore>
    ): RiskScore
}

