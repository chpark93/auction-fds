package com.ch.fds.core.model

import java.math.BigDecimal
import java.time.Instant
import java.util.*

sealed interface UserActivity {
    val activityId: UUID
    val userId: String
    val occurredAt: Instant
    val activityType: String
}

/**
 * 경매 입찰
 */
data class BidActivity(
    override val activityId: UUID,
    override val userId: String,
    override val occurredAt: Instant,
    val auctionId: String,
    val bidAmount: BigDecimal,
    val previousBidAmount: BigDecimal?,
    val timeUntilAuctionEnd: Long, // 경매 종료까지 남은 시간
    val bidCount: Int // 사용자의 경매에서의 총 입찰 횟수
) : UserActivity {
    override val activityType: String = "BID"
}

/**
 * 결제
 */
data class PaymentActivity(
    override val activityId: UUID,
    override val userId: String,
    override val occurredAt: Instant,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val cardLastFour: String,
    val cardType: String,
    val merchantId: String,
    val ipAddress: String
) : UserActivity {
    override val activityType: String = "PAYMENT"
}

