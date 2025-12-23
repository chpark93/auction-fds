package com.ch.fds.common.event

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@type"
)
@JsonSubTypes(
    JsonSubTypes.Type(
        value = BidActivityDetectedEvent::class,
        name = "BidActivityDetected"
    ),
    JsonSubTypes.Type(
        value = PaymentActivityDetectedEvent::class,
        name = "PaymentActivityDetected"
    ),
    JsonSubTypes.Type(
        value = FraudDetectedEvent::class,
        name = "FraudDetected"
    ),
    JsonSubTypes.Type(
        value = RiskScoreUpdatedEvent::class,
        name = "RiskScoreUpdated"
    )
)
sealed interface ActivityEvent : DomainEvent

data class BidActivityDetectedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val aggregateId: String,
    override val version: Long,
    override val occurredAt: Instant = Instant.now(),
    val activityId: UUID,
    val auctionId: String,
    val bidAmount: BigDecimal,
    val previousBidAmount: BigDecimal?,
    val timeUntilAuctionEnd: Long,
    val bidCount: Int
) : ActivityEvent {
    override val eventType: String = "BidActivityDetected"
    
    override fun payload(): Map<String, Any> {
        return mapOf(
            "activityId" to activityId.toString(),
            "auctionId" to auctionId,
            "bidAmount" to bidAmount.toString(),
            "previousBidAmount" to (previousBidAmount?.toString() ?: "null"),
            "timeUntilAuctionEnd" to timeUntilAuctionEnd,
            "bidCount" to bidCount
        )
    }
}

data class PaymentActivityDetectedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val aggregateId: String,
    override val version: Long,
    override val occurredAt: Instant = Instant.now(),
    val activityId: UUID,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val cardLastFour: String,
    val merchantId: String
) : ActivityEvent {
    override val eventType: String = "PaymentActivityDetected"
    
    override fun payload(): Map<String, Any> {
        return mapOf(
            "activityId" to activityId.toString(),
            "orderId" to orderId,
            "amount" to amount.toString(),
            "currency" to currency,
            "cardLastFour" to cardLastFour,
            "merchantId" to merchantId
        )
    }
}

data class FraudDetectedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val aggregateId: String,
    override val version: Long,
    override val occurredAt: Instant = Instant.now(),
    val activityId: UUID,
    val ruleId: String,
    val ruleName: String,
    val riskScore: Int,
    val reason: String,
    val metadata: Map<String, Any> = emptyMap()
) : ActivityEvent {
    override val eventType: String = "FraudDetected"
    
    override fun payload(): Map<String, Any> {
        return mapOf(
            "activityId" to activityId.toString(),
            "ruleId" to ruleId,
            "ruleName" to ruleName,
            "riskScore" to riskScore,
            "reason" to reason,
            "metadata" to metadata
        )
    }
}

data class RiskScoreUpdatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val aggregateId: String,
    override val version: Long,
    override val occurredAt: Instant = Instant.now(),
    val previousScore: Int,
    val newScore: Int,
    val reason: String
) : ActivityEvent {
    override val eventType: String = "RiskScoreUpdated"
    
    override fun payload(): Map<String, Any> {
        return mapOf(
            "previousScore" to previousScore,
            "newScore" to newScore,
            "reason" to reason
        )
    }
}

