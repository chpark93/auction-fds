package com.ch.fds.application.api

import com.ch.fds.common.event.EventStore
import kotlinx.coroutines.flow.toList
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/debug")
class DebugController(
    private val eventStore: EventStore
) {
    
    /**
     * 특정 사용자의 모든 이벤트 조회.
     * Event Sourcing 검증:
     * - RiskScoreUpdated 이벤트 저장 확인
     * - BidActivityDetected 이벤트 저장 확인
     * - 이벤트 순서 확인 (version 순)
     */
    @GetMapping("/events/{userId}")
    suspend fun getUserEvents(
        @PathVariable userId: String
    ): EventHistoryResponse {
        val events = eventStore.load(
            aggregateId = userId
        ).toList()
        
        return EventHistoryResponse(
            userId = userId,
            eventCount = events.size,
            events = events.map { event ->
                EventInfo(
                    eventId = event.eventId.toString(),
                    eventType = event.eventType,
                    version = event.version,
                    occurredAt = event.occurredAt.toString(),
                    payload = event.payload()
                )
            }
        )
    }
    
    /**
     * 특정 타입의 모든 이벤트 조회.
     */
    @GetMapping("/events/by-type/{eventType}")
    suspend fun getEventsByType(
        @PathVariable eventType: String,
        @RequestParam(defaultValue = "10") limit: Int
    ): EventTypeResponse {
        val events = eventStore.findByEventType(eventType)
            .toList()
            .take(limit)
        
        return EventTypeResponse(
            eventType = eventType,
            eventCount = events.size,
            events = events.map { event ->
                EventInfo(
                    eventId = event.eventId.toString(),
                    eventType = event.eventType,
                    version = event.version,
                    occurredAt = event.occurredAt.toString(),
                    payload = event.payload()
                )
            }
        )
    }
    
    /**
     * 시스템 상태 확인
     */
    @GetMapping("/health")
    fun health(): Map<String, String> {
        return mapOf(
            "status" to "OK",
            "service" to "FDS",
            "version" to "1.0.0"
        )
    }
}

data class EventHistoryResponse(
    val userId: String,
    val eventCount: Int,
    val events: List<EventInfo>
)

data class EventTypeResponse(
    val eventType: String,
    val eventCount: Int,
    val events: List<EventInfo>
)

data class EventInfo(
    val eventId: String,
    val eventType: String,
    val version: Long,
    val occurredAt: String,
    val payload: Map<String, Any>
)

