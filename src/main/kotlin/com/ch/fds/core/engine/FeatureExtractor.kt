package com.ch.fds.core.engine

import com.ch.fds.core.model.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

object FeatureExtractor {
    
    fun extract(
        activity: UserActivity,
        profile: UserRiskProfile
    ): TransactionFeatures {
        val now = activity.occurredAt
        
        return TransactionFeatures(
            userId = activity.userId,
            accountAgeSeconds = calculateAccountAge(profile),
            activityType = mapActivityType(activity.activityType),
            amount = extractAmount(activity),
            timestamp = now.epochSecond,
            activityCountLast1Min = countActivitiesWithin(profile, Duration.ofMinutes(1), now),
            activityCountLast5Min = countActivitiesWithin(profile, Duration.ofMinutes(5), now),
            activityCountLast1Hour = countActivitiesWithin(profile, Duration.ofHours(1), now),
            hourOfDay = extractHourOfDay(now),
            dayOfWeek = extractDayOfWeek(now),
            currentRiskScore = profile.currentRiskScore.value,
            previousRiskScore = profile.currentRiskScore.value, // TODO: 이전 점수 추적 필요
            totalActivityCount = profile.recentActivities.size,
            timeUntilAuctionEnd = extractTimeUntilAuctionEnd(activity),
            samAuctionBidCount = countSameAuctionBids(activity, profile),
            paymentMethodType = extractPaymentMethodType(activity),
            amountRatioToPrevious = calculateAmountRatio(activity, profile)
        )
    }
    
    private fun calculateAccountAge(
        profile: UserRiskProfile
    ): Long {
        if (profile.recentActivities.isEmpty()) {
            return 0L
        }
        
        val firstActivityTime = profile.recentActivities.minOf { it.occurredAt }
        val now = Instant.now()
        
        return Duration.between(firstActivityTime, now).seconds.coerceAtLeast(0L)
    }
    
    private fun mapActivityType(
        activityType: String
    ): Int {
        return when (activityType) {
            "BID" -> TransactionFeatures.ACTIVITY_TYPE_BID
            "PAYMENT" -> TransactionFeatures.ACTIVITY_TYPE_PAYMENT
            "TRADE" -> TransactionFeatures.ACTIVITY_TYPE_TRADE
            else -> 0
        }
    }
    
    private fun extractAmount(
        activity: UserActivity
    ): Double {
        return when (activity) {
            is BidActivity -> activity.bidAmount.toDouble()
            is PaymentActivity -> activity.amount.toDouble()
        }
    }

    private fun countActivitiesWithin(
        profile: UserRiskProfile,
        duration: Duration,
        referenceTime: Instant
    ): Int {
        return profile.getActivitiesWithin(
            duration = duration,
            referenceTime = referenceTime
        ).size
    }
    
    private fun extractHourOfDay(
        timestamp: Instant
    ): Int {
        return timestamp.atZone(ZoneId.systemDefault()).hour
    }
    
    private fun extractDayOfWeek(
        timestamp: Instant
    ): Int {
        return timestamp.atZone(ZoneId.systemDefault()).dayOfWeek.value
    }
    
    private fun extractTimeUntilAuctionEnd(
        activity: UserActivity
    ): Long? {
        return when (activity) {
            is BidActivity -> activity.timeUntilAuctionEnd
            else -> null
        }
    }
    
    private fun countSameAuctionBids(
        activity: UserActivity,
        profile: UserRiskProfile
    ): Int? {
        if (activity !is BidActivity) return null
        
        return profile.recentBids.count { it.auctionId == activity.auctionId } + 1
    }
    
    private fun extractPaymentMethodType(
        activity: UserActivity
    ): Int? {
        if (activity !is PaymentActivity) return null
        
        // TODO: PaymentActivity에 paymentMethod 필드 추가 필요
        // 임시로 기본값 반환
        return TransactionFeatures.PAYMENT_METHOD_CARD
    }
    
    private fun calculateAmountRatio(
        activity: UserActivity,
        profile: UserRiskProfile
    ): Double? {
        if (activity !is PaymentActivity) return null
        
        val previousPayments = profile.recentPayments
        if (previousPayments.isEmpty()) return null
        
        val previousAmount = previousPayments.last().amount
        if (previousAmount.toDouble() == 0.0) return null
        
        return activity.amount.toDouble() / previousAmount.toDouble()
    }
}

