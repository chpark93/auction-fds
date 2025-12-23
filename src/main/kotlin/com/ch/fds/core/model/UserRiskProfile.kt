package com.ch.fds.core.model

import com.ch.fds.common.event.DomainEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.fold
import java.time.Duration
import java.time.Instant

data class UserRiskProfile(
    val userId: String,
    val currentRiskScore: RiskScore = RiskScore.ZERO,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val recentBids: List<BidActivity> = emptyList(),
    val recentPayments: List<PaymentActivity> = emptyList(),
    val totalFraudDetections: Int = 0,
    val lastActivityAt: Instant? = null,
    val version: Long = 0
) {
    companion object {
        private const val MAX_RECENT_BIDS = 10
        private const val MAX_RECENT_PAYMENTS = 5
        private const val MAX_RECENT_TRADES = 5
        
        fun create(
            userId: String
        ): UserRiskProfile {
            return UserRiskProfile(
                userId = userId
            )
        }
        
        suspend fun loadFromHistory(
            userId: String,
            events: Flow<DomainEvent>
        ): UserRiskProfile {
            return events.fold(create(
                userId = userId
            )) { profile, event ->
                profile.apply(event)
            }
        }
    }
    
    private fun apply(
        event: DomainEvent
    ): UserRiskProfile {
        // TODO: 실제 이벤트 타입 -> 상태 업데이트 로직 구현 필요

        return this.copy(
            version = event.version
        )
    }
    
    fun addBidActivity(
        bid: BidActivity
    ): UserRiskProfile {
        val updatedBids = (recentBids + bid).takeLast(MAX_RECENT_BIDS)

        return copy(
            recentBids = updatedBids,
            lastActivityAt = bid.occurredAt
        )
    }
    
    fun addPaymentActivity(
        payment: PaymentActivity
    ): UserRiskProfile {
        val updatedPayments = (recentPayments + payment).takeLast(MAX_RECENT_PAYMENTS)

        return copy(
            recentPayments = updatedPayments,
            lastActivityAt = payment.occurredAt
        )
    }
    
    fun updateRiskScore(
        newScore: RiskScore
    ): UserRiskProfile {
        return copy(
            currentRiskScore = newScore,
            riskLevel = newScore.level
        )
    }
    
    fun incrementFraudDetections(): UserRiskProfile {
        return copy(
            totalFraudDetections = totalFraudDetections + 1
        )
    }
    
    /**
     * 특정 기간 -> 입찰 활동 조회.
     */
    fun getRecentBidsWithin(
        duration: Duration,
        referenceTime: Instant = Instant.now()
    ): List<BidActivity> {
        val threshold = referenceTime.minus(duration)

        return recentBids.filter { it.occurredAt.isAfter(threshold) }
    }
    
    /**
     * 특정 경매 -> 입찰 조회.
     */
    fun getBidsForAuction(
        auctionId: String
    ): List<BidActivity> {
        return recentBids.filter { it.auctionId == auctionId }
    }
    
    /**
     * 특정 경매 -> 특정 기간 내의 입찰 조회.
     */
    fun getBidsForAuctionWithin(
        auctionId: String,
        duration: Duration,
        referenceTime: Instant = Instant.now()
    ): List<BidActivity> {
        val threshold = referenceTime.minus(duration)

        return recentBids.filter { 
            it.auctionId == auctionId && it.occurredAt.isAfter(threshold)
        }
    }
    
    /**
     * 특정 기간 내의 결제 조회.
     */
    fun getRecentPaymentsWithin(
        duration: Duration,
        referenceTime: Instant = Instant.now()
    ): List<PaymentActivity> {
        val threshold = referenceTime.minus(duration)

        return recentPayments.filter { it.occurredAt.isAfter(threshold) }
    }
}

