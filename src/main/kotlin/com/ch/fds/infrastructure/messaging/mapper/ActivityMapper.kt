package com.ch.fds.infrastructure.messaging.mapper

import com.ch.fds.core.model.BidActivity
import com.ch.fds.infrastructure.messaging.dto.BidPlacedEvent
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ActivityMapper {
    
    fun toDomainModel(
        event: BidPlacedEvent
    ): BidActivity {
        return BidActivity(
            activityId = UUID.fromString(event.eventId),
            userId = event.userId,
            occurredAt = event.occurredAt,
            auctionId = event.auctionId,
            bidAmount = event.bidAmount,
            previousBidAmount = event.previousBidAmount,
            timeUntilAuctionEnd = event.calculateTimeUntilAuctionEnd(),
            bidCount = event.bidCount
        )
    }
}

