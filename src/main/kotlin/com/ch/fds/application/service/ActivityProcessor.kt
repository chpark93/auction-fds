package com.ch.fds.application.service

import com.ch.fds.application.port.`in`.IngestActivityUseCase
import com.ch.fds.common.event.BidActivityDetectedEvent
import com.ch.fds.common.event.DomainEvent
import com.ch.fds.common.event.EventStore
import com.ch.fds.common.event.PaymentActivityDetectedEvent
import com.ch.fds.core.model.*
import com.ch.fds.core.service.RiskAnalysisService
import com.ch.fds.infrastructure.messaging.FraudAlertProducer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class ActivityProcessor(
    private val eventStore: EventStore,
    private val riskAnalysisService: RiskAnalysisService,
    @param:Autowired(required = false)
    private val fraudAlertProducer: FraudAlertProducer?
) : IngestActivityUseCase {

    private val logger = LoggerFactory.getLogger(ActivityProcessor::class.java)
    
    private val alertScope = CoroutineScope(Dispatchers.Default)
    private val userLocks = ConcurrentHashMap<String, Mutex>()
    
    override suspend fun ingest(
        activity: UserActivity
    ): AggregatedDetectionResult {
        logger.info(
            "Processing activity: type={}, userId={}, activityId={}",
            activity.activityType,
            activity.userId,
            activity.activityId
        )
        
        // Mutex 획득 -> 동시성 충돌 방지
        val userLock = userLocks.computeIfAbsent(activity.userId) { Mutex() }
        
        return userLock.withLock {
            try {
                // 사용자 프로필 로드
                val profile = loadUserProfile(
                    userId = activity.userId
                )
                logger.debug("Loaded user profile: userId={}, version={}", activity.userId, profile.version)
                
                // 활동을 프로파일에 추가하고 이벤트 생성 및 저장
                val updatedProfile = addActivityToProfile(
                    profile = profile,
                    activity = activity
                )
                
                // 활동에 맞는 도메인 이벤트 생성 및 저장
                val activityEvent = createActivityEvent(
                    activity = activity,
                    version = profile.version + 1
                )
                
                eventStore.save(activityEvent)
                logger.debug(
                    "Event saved: eventId={}, eventType={}, userId={}",
                    activityEvent.eventId,
                    activityEvent.eventType,
                    activityEvent.aggregateId
                )
                
                // 탐지 분석 수행
                val result = riskAnalysisService.analyze(
                    activity = activity,
                    profile = updatedProfile
                )

                logger.info(
                    "Analysis completed: userId={}, decision={}, totalRisk={}",
                    activity.userId,
                    result.decision,
                    result.totalRiskScore.value
                )
                
                // 비동기로 알림 발송 (메인 트랜잭션을 블로킹하지 않음)
                // Kafka가 활성화되어 있을 때만 실행
                if (result.decision != FraudDecision.APPROVE && fraudAlertProducer != null) {
                    alertScope.launch {
                        fraudAlertProducer.sendAlert(result)
                    }
                }

                result
                
            } catch (e: Exception) {
                logger.error("Error processing activity: userId=${activity.userId}", e)

                throw ActivityProcessingException("Failed to process activity for user ${activity.userId}", e)
            }
        }
    }
    
    private suspend fun loadUserProfile(
        userId: String
    ): UserRiskProfile {
        val events = eventStore.load(
            aggregateId = userId
        )

        return UserRiskProfile.loadFromHistory(
            userId = userId,
            events = events
        )
    }

    private fun addActivityToProfile(
        profile: UserRiskProfile,
        activity: UserActivity
    ): UserRiskProfile {

        return when (activity) {
            is BidActivity -> profile.addBidActivity(
                bid = activity
            )
            is PaymentActivity -> profile.addPaymentActivity(
                payment = activity
            )
        }
    }
    
    private fun createActivityEvent(
        activity: UserActivity,
        version: Long
    ): DomainEvent {
        return when (activity) {
            is BidActivity -> BidActivityDetectedEvent(
                aggregateId = activity.userId,
                version = version,
                occurredAt = activity.occurredAt,
                activityId = activity.activityId,
                auctionId = activity.auctionId,
                bidAmount = activity.bidAmount,
                previousBidAmount = activity.previousBidAmount,
                timeUntilAuctionEnd = activity.timeUntilAuctionEnd,
                bidCount = activity.bidCount
            )
            is PaymentActivity -> PaymentActivityDetectedEvent(
                aggregateId = activity.userId,
                version = version,
                occurredAt = activity.occurredAt,
                activityId = activity.activityId,
                orderId = activity.orderId,
                amount = activity.amount,
                currency = activity.currency,
                cardLastFour = activity.cardLastFour,
                merchantId = activity.merchantId
            )
        }
    }
}

class ActivityProcessingException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
