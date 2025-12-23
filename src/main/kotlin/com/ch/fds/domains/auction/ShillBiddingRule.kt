package com.ch.fds.domains.auction

import com.ch.fds.core.model.BidActivity
import com.ch.fds.core.model.DetectionResult
import com.ch.fds.core.model.RiskScore
import com.ch.fds.core.model.UserRiskProfile
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class ShillBiddingRule : AuctionRule(
    ruleId = "AUCTION_SHILL_BIDDING",
    ruleName = "Shill Bidding Detection"
) {
    private val logger = LoggerFactory.getLogger(ShillBiddingRule::class.java)
    
    companion object {
        // 동일 경매에 5회 이상 입찰: MEDIUM 리스크
        private const val WARNING_BID_COUNT = 5
        // 동일 경매에 10회 이상 입찰: HIGH 리스크
        private const val CRITICAL_BID_COUNT = 10
        // 소액씩 반복 입찰: 추가 리스크
        // 5%
        private const val SMALL_INCREMENT_THRESHOLD = 0.05
    }
    
    override suspend fun doEvaluate(
        activity: BidActivity,
        profile: UserRiskProfile
    ): DetectionResult {
        val bidCount = activity.bidCount
        
        logger.debug(
            "Evaluating shill bidding: userId={}, auctionId={}, bidCount={}",
            activity.userId,
            activity.auctionId,
            bidCount
        )
        
        // 입찰 횟수 기반 판단
        val countBasedRisk = when {
            bidCount >= CRITICAL_BID_COUNT -> RiskScore.HIGH
            bidCount >= WARNING_BID_COUNT -> RiskScore.MEDIUM
            else -> RiskScore.ZERO
        }
        
        // 입찰 증가 패턴 분석
        val incrementRisk = analyzeIncrementPattern(
            activity = activity,
            profile = profile
        )
        
        // 리스크 점수 계산
        val finalRisk = countBasedRisk + incrementRisk
        
        return if (finalRisk.value > 0) {
            DetectionResult.fraud(
                ruleId = ruleId,
                ruleName = ruleName,
                riskScore = finalRisk,
                reason = buildReasonMessage(
                    bidCount = bidCount,
                    previousAmount = activity.previousBidAmount,
                    currentAmount = activity.bidAmount
                ),
                metadata = mapOf(
                    "bidCount" to bidCount,
                    "auctionId" to activity.auctionId,
                    "bidAmount" to activity.bidAmount.toString(),
                    "previousBidAmount" to (activity.previousBidAmount?.toString() ?: "N/A")
                )
            )
        } else {
            DetectionResult.normal(
                ruleId = ruleId,
                ruleName = ruleName,
                reason = "Bidding pattern is normal"
            )
        }
    }
    
    private fun analyzeIncrementPattern(
        activity: BidActivity,
        profile: UserRiskProfile
    ): RiskScore {
        val previousAmount = activity.previousBidAmount ?: return RiskScore.ZERO
        
        if (previousAmount <= BigDecimal.ZERO) {
            return RiskScore.ZERO
        }
        
        val incrementRatio = (activity.bidAmount - previousAmount)
            .divide(previousAmount, 4, java.math.RoundingMode.HALF_UP)
            .toDouble()
        
        return if (incrementRatio < SMALL_INCREMENT_THRESHOLD && activity.bidCount >= WARNING_BID_COUNT) {
            // 소액 증가 + 반복 입찰
            RiskScore.LOW
        } else {
            RiskScore.ZERO
        }
    }
    
    private fun buildReasonMessage(
        bidCount: Int,
        previousAmount: BigDecimal?,
        currentAmount: BigDecimal
    ): String {
        return buildString {
            append("Suspicious bidding pattern detected: ")
            append("$bidCount bids in the same auction")
            
            if (previousAmount != null && previousAmount > BigDecimal.ZERO) {
                val increment = currentAmount - previousAmount
                append(", small increment detected ($increment)")
            }
        }
    }
}

