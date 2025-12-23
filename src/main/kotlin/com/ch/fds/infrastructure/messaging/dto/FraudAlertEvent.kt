package com.ch.fds.infrastructure.messaging.dto

import com.ch.fds.core.model.FraudDecision
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class FraudAlertEvent(
    @field:JsonProperty("user_id")
    val userId: String,
    
    @field:JsonProperty("decision")
    val decision: FraudDecision,
    
    @field:JsonProperty("total_risk_score")
    val totalRiskScore: Int,
    
    @field:JsonProperty("reason")
    val reason: String,
    
    @field:JsonProperty("detected_rules")
    val detectedRules: List<String>,
    
    @field:JsonProperty("timestamp")
    val timestamp: Instant = Instant.now(),
    
    @field:JsonProperty("metadata")
    val metadata: Map<String, Any> = emptyMap()
)

