package com.ch.fds.infrastructure.messaging

import com.ch.fds.core.model.AggregatedDetectionResult
import com.ch.fds.core.model.FraudDecision
import com.ch.fds.infrastructure.messaging.dto.FraudAlertEvent
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "fds.kafka", name = ["enabled"], havingValue = "true", matchIfMissing = false)
class FraudAlertProducer(
    private val kafkaTemplate: KafkaTemplate<String, FraudAlertEvent>,
    @param:Value("\${fds.kafka.topics.detection-results}")
    private val alertTopic: String
) {
    private val logger = LoggerFactory.getLogger(FraudAlertProducer::class.java)
    
    suspend fun sendAlert(
        result: AggregatedDetectionResult
    ) {
        if (result.decision == FraudDecision.APPROVE) {
            return
        }
        
        try {
            val alertEvent = FraudAlertEvent(
                userId = result.userId,
                decision = result.decision,
                totalRiskScore = result.totalRiskScore.value,
                reason = buildReasonMessage(
                    result = result
                ),
                detectedRules = result.fraudDetections.map { it.ruleName },
                metadata = buildMetadata(
                    result = result
                )
            )
            
            kafkaTemplate.send(
                alertTopic,
                result.userId,
                alertEvent
            ).await()
            
            logger.warn(
                "🚨 FRAUD ALERT SENT: userId={}, decision={}, riskScore={}, rules={}",
                result.userId,
                result.decision,
                result.totalRiskScore.value,
                result.fraudDetections.joinToString { it.ruleName }
            )
            
        } catch (e: Exception) {
            logger.error("Failed to send fraud alert for user: ${result.userId}", e)
        }
    }
    
    private fun buildReasonMessage(
        result: AggregatedDetectionResult
    ): String {
        return when {
            result.fraudDetections.isEmpty() -> {
                "Risk score elevated: ${result.totalRiskScore.value}"
            }
            result.fraudDetections.size == 1 -> {
                result.fraudDetections.first().reason
            }
            else -> {
                "Multiple fraud patterns detected: ${result.fraudDetections.joinToString { it.ruleName }}"
            }
        }
    }
    
    private fun buildMetadata(
        result: AggregatedDetectionResult
    ): Map<String, Any> {
        return mapOf(
            "activityId" to result.activityId.toString(),
            "activityType" to result.activityType,
            "fraudCount" to result.fraudDetections.size,
            "maxRiskScore" to result.maxRiskScore.value,
            "analyzedAt" to result.analyzedAt.toString()
        )
    }
}

