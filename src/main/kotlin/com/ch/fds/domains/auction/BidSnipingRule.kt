package com.ch.fds.domains.auction

import com.ch.fds.core.model.BidActivity
import com.ch.fds.core.model.DetectionResult
import com.ch.fds.core.model.RiskScore
import com.ch.fds.core.model.UserRiskProfile
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class BidSnipingRule : AuctionRule(
    ruleId = "AUCTION_BID_SNIPING",
    ruleName = "Bid Sniping Detection"
) {
    private val logger = LoggerFactory.getLogger(BidSnipingRule::class.java)
    
    companion object {
        // 시간 기반 임계값
        // 10초 내 동일 경매에 3회 이상 입찰: CRITICAL 리스크
        private val CRITICAL_TIME_WINDOW = Duration.ofSeconds(10)
        private const val CRITICAL_BID_COUNT = 3

        // 30초 내 동일 경매에 3회 이상 입찰: HIGH 리스크
        private val HIGH_TIME_WINDOW = Duration.ofSeconds(30)
        private const val HIGH_BID_COUNT = 3

        // 60초 내 동일 경매에 5회 이상 입찰: MEDIUM 리스크
        private val MEDIUM_TIME_WINDOW = Duration.ofSeconds(60)
        private const val MEDIUM_BID_COUNT = 5
        
        // 경매 종료 시간 임계값
        // 경매 종료 30초 전 입찰: 추가 리스크
        private const val AUCTION_END_CRITICAL_SECONDS = 30L
        private const val AUCTION_END_WARNING_SECONDS = 60L
    }
    
    override suspend fun doEvaluate(
        activity: BidActivity,
        profile: UserRiskProfile
    ): DetectionResult {
        logger.debug(
            "Evaluating bid sniping: userId={}, auctionId={}, timeUntilEnd={}s",
            activity.userId,
            activity.auctionId,
            activity.timeUntilAuctionEnd
        )
        
        // 짧은 시간 내 반복 입찰 패턴 분석
        val rapidBiddingScore = analyzeRapidBidding(
            activity = activity,
            profile = profile
        )
        
        // 경매 종료 직전 입찰 분석
        val auctionEndTimingScore = analyzeAuctionEndTiming(
            activity = activity
        )
        
        // 최종 리스크 점수
        val finalScore = maxOf(rapidBiddingScore, auctionEndTimingScore)
        
        return if (finalScore.value > 0) {
            DetectionResult.fraud(
                ruleId = ruleId,
                ruleName = ruleName,
                riskScore = finalScore,
                reason = buildReasonMessage(
                    activity = activity,
                    profile = profile,
                    rapidScore = rapidBiddingScore,
                    timingScore = auctionEndTimingScore
                ),
                metadata = buildMetadata(
                    activity = activity,
                    profile = profile
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
    
    /**
     * 짧은 시간 내 반복 입찰 패턴 분석.
     */
    private fun analyzeRapidBidding(
        activity: BidActivity,
        profile: UserRiskProfile
    ): RiskScore {
        val auctionId = activity.auctionId
        
        // 현재 입찰을 포함 최근 입찰 목록
        val allBids = profile.recentBids + activity
        
        // 10초 내 입찰 횟수
        val bidsIn10Sec = allBids.filter {
            it.auctionId == auctionId &&
            Duration.between(it.occurredAt, activity.occurredAt).abs() <= CRITICAL_TIME_WINDOW
        }.size
        
        if (bidsIn10Sec >= CRITICAL_BID_COUNT) {
            logger.warn("Critical rapid bidding detected: {} bids in 10 seconds", bidsIn10Sec)
            return RiskScore.CRITICAL
        }
        
        // 30초 내 입찰 횟수
        val bidsIn30Sec = allBids.filter {
            it.auctionId == auctionId &&
            Duration.between(it.occurredAt, activity.occurredAt).abs() <= HIGH_TIME_WINDOW
        }.size
        
        if (bidsIn30Sec >= HIGH_BID_COUNT) {
            logger.warn("High rapid bidding detected: {} bids in 30 seconds", bidsIn30Sec)
            return RiskScore.HIGH
        }
        
        // 60초 내 입찰 횟수
        val bidsIn60Sec = allBids.filter {
            it.auctionId == auctionId &&
            Duration.between(it.occurredAt, activity.occurredAt).abs() <= MEDIUM_TIME_WINDOW
        }.size
        
        if (bidsIn60Sec >= MEDIUM_BID_COUNT) {
            logger.info("Medium rapid bidding detected: {} bids in 60 seconds", bidsIn60Sec)
            return RiskScore.MEDIUM
        }
        
        return RiskScore.ZERO
    }
    
    /**
     * 경매 종료 직전 입찰 분석.
     */
    private fun analyzeAuctionEndTiming(
        activity: BidActivity
    ): RiskScore {
        val timeUntilEnd = activity.timeUntilAuctionEnd
        
        return when {
            timeUntilEnd <= AUCTION_END_CRITICAL_SECONDS -> {
                logger.warn("Bid placed very close to auction end: {} seconds", timeUntilEnd)
                RiskScore.HIGH
            }
            timeUntilEnd <= AUCTION_END_WARNING_SECONDS -> {
                logger.info("Bid placed close to auction end: {} seconds", timeUntilEnd)
                RiskScore.MEDIUM
            }
            else -> RiskScore.ZERO
        }
    }
    
    /**
     * 탐지 메시지 생성.
     */
    private fun buildReasonMessage(
        activity: BidActivity,
        profile: UserRiskProfile,
        rapidScore: RiskScore,
        timingScore: RiskScore
    ): String {
        val reasons = mutableListOf<String>()
        
        if (rapidScore.value > 0) {
            val recentBidCount = (profile.getBidsForAuctionWithin(
                auctionId = activity.auctionId,
                duration = MEDIUM_TIME_WINDOW,
                referenceTime = activity.occurredAt
            ) + activity).size
            
            reasons.add("Rapid bidding detected: $recentBidCount bids in short time")
        }
        
        if (timingScore.value > 0) {
            reasons.add("Bid placed ${activity.timeUntilAuctionEnd}s before auction end")
        }
        
        return reasons.joinToString("; ")
    }
    
    /**
     * 메타데이터 생성.
     */
    private fun buildMetadata(
        activity: BidActivity,
        profile: UserRiskProfile
    ): Map<String, Any> {
        val bidsIn10Sec = profile.getBidsForAuctionWithin(
            auctionId = activity.auctionId,
            duration = CRITICAL_TIME_WINDOW,
            referenceTime = activity.occurredAt
        ).size + 1
        
        val bidsIn30Sec = profile.getBidsForAuctionWithin(
            auctionId = activity.auctionId,
            duration = HIGH_TIME_WINDOW,
            referenceTime = activity.occurredAt
        ).size + 1
        
        return mapOf(
            "auctionId" to activity.auctionId,
            "timeUntilAuctionEnd" to activity.timeUntilAuctionEnd,
            "bidsIn10Seconds" to bidsIn10Sec,
            "bidsIn30Seconds" to bidsIn30Sec,
            "bidAmount" to activity.bidAmount.toString()
        )
    }
}
