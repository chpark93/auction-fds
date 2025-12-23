package com.ch.fds.infrastructure.messaging.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

data class BidPlacedEvent(
    @field:JsonProperty("event_id")
    val eventId: String,
    
    @field:JsonProperty("user_id")
    val userId: String,
    
    @field:JsonProperty("auction_id")
    val auctionId: String,
    
    @field:JsonProperty("bid_amount")
    val bidAmount: BigDecimal,
    
    @field:JsonProperty("previous_bid_amount")
    val previousBidAmount: BigDecimal?,
    
    @field:JsonProperty("auction_end_time")
    val auctionEndTime: Instant,
    
    @field:JsonProperty("bid_count")
    val bidCount: Int,
    
    @field:JsonProperty("occurred_at")
    val occurredAt: Instant,
    
    @field:JsonProperty("metadata")
    val metadata: Map<String, Any> = emptyMap()
) {

    fun calculateTimeUntilAuctionEnd(): Long {
        val duration = Duration.between(occurredAt, auctionEndTime)

        return duration.seconds.coerceAtLeast(0)
    }
}

