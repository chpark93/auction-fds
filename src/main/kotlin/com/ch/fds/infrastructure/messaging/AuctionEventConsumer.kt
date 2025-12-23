package com.ch.fds.infrastructure.messaging

import com.ch.fds.application.port.`in`.IngestActivityUseCase
import com.ch.fds.infrastructure.messaging.dto.BidPlacedEvent
import com.ch.fds.infrastructure.messaging.mapper.ActivityMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "fds.kafka",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class AuctionEventConsumer(
    private val ingestActivityUseCase: IngestActivityUseCase,
    private val activityMapper: ActivityMapper
) {
    private val logger = LoggerFactory.getLogger(AuctionEventConsumer::class.java)

    private val consumerScope = CoroutineScope(Dispatchers.Default)
    
    @KafkaListener(
        topics = ["\${fds.kafka.topics.auction-events}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun onBidPlaced(
        event: BidPlacedEvent,
        acknowledgment: Acknowledgment
    ) {
        logger.info(
            "Received bid event: eventId={}, userId={}, auctionId={}",
            event.eventId,
            event.userId,
            event.auctionId
        )
        
        consumerScope.launch {
            try {
                val bidActivity = activityMapper.toDomainModel(
                    event = event
                )
                
                logger.debug("Converted to domain model: activityId={}", bidActivity.activityId)
                
                // 사기 탐지 분석
                val result = ingestActivityUseCase.ingest(
                    activity = bidActivity
                )
                
                logger.info(
                    "Activity processed: userId={}, decision={}, riskScore={}",
                    result.userId,
                    result.decision,
                    result.totalRiskScore.value
                )
                
                // 사기 탐지 -> 추가 로깅
                if (result.fraudDetections.isNotEmpty()) {
                    logger.warn(
                        "FRAUD DETECTED: userId={}, rules={}, decision={}",
                        result.userId,
                        result.fraudDetections.joinToString { it.ruleName },
                        result.decision
                    )
                }
                
                // offset 커밋
                acknowledgment.acknowledge()
                
            } catch (e: Exception) {
                logger.error(
                    "Failed to process bid event: eventId=${event.eventId}, userId=${event.userId}",
                    e
                )

                // TODO: 에러 발생 시 offset을 커밋하지 않음 -> Kafka가 메시지를 재전송하여 재처리 시도
                // Dead Letter Queue(DLQ) 사용?
            }
        }
    }
}

