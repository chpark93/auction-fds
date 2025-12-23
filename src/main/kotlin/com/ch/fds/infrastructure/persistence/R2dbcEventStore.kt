package com.ch.fds.infrastructure.persistence

import com.ch.fds.common.event.ActivityEvent
import com.ch.fds.common.event.DomainEvent
import com.ch.fds.common.event.EventStore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

@Repository
class R2dbcEventStore(
    private val eventEntityRepository: EventEntityRepository,
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper
) : EventStore {
    
    private val logger = LoggerFactory.getLogger(R2dbcEventStore::class.java)
    
    override suspend fun save(
        event: DomainEvent
    ): DomainEvent {
        logger.debug("Saving event: type={}, aggregateId={}", event.eventType, event.aggregateId)
        
        val payloadJson = objectMapper.writeValueAsString(event)
        val eventId = event.eventId
        
        databaseClient.sql("""
            INSERT INTO domain_events (id, aggregate_id, aggregate_type, version, event_type, payload, occurred_at, created_at)
            VALUES (:id, :aggregateId, :aggregateType, :version, :eventType, :payload::jsonb, :occurredAt, :createdAt)
        """.trimIndent())
            .bind("id", eventId)
            .bind("aggregateId", event.aggregateId)
            .bind("aggregateType", "UserRiskProfile")
            .bind("version", event.version)
            .bind("eventType", event.eventType)
            .bind("payload", payloadJson)
            .bind("occurredAt", event.occurredAt)
            .bind("createdAt", Instant.now())
            .fetch()
            .awaitRowsUpdated()
        
        logger.info("Event saved: id={}, type={}", eventId, event.eventType)
        return event
    }
    
    override suspend fun saveAll(
        events: Flow<DomainEvent>
    ): Long {
        logger.debug("Saving batch of events")
        
        val entities = events.map { toEntity(it) }
        val count = eventEntityRepository.saveAll(entities).count()
        
        logger.info("Saved {} events", count)
        return count.toLong()
    }
    
    override fun load(
        aggregateId: String
    ): Flow<DomainEvent> {
        logger.debug("Loading events for aggregate: {}", aggregateId)
        
        return eventEntityRepository.findByAggregateIdOrderByVersionAsc(
            aggregateId = aggregateId
        ).map { toDomainEvent(it) }
    }
    
    override fun loadAfterVersion(
        aggregateId: String,
        afterVersion: Long
    ): Flow<DomainEvent> {
        logger.debug("Loading events for aggregate: {}, afterVersion: {}", aggregateId, afterVersion)
        
        return eventEntityRepository.findByAggregateIdAndVersionGreaterThanOrderByVersionAsc(
            aggregateId = aggregateId,
            afterVersion = afterVersion
        ).map { toDomainEvent(it) }
    }
    
    override fun findByEventType(
        eventType: String
    ): Flow<DomainEvent> {
        logger.debug("Loading events by type: {}", eventType)
        
        return eventEntityRepository.findByEventTypeOrderByOccurredAtDesc(
            eventType = eventType
        ).map { toDomainEvent(it) }
    }
    
    private fun toEntity(
        event: DomainEvent
    ): EventEntity {
        val payloadJson = objectMapper.writeValueAsString(event)
        
        return EventEntity(
            id = event.eventId,
            aggregateId = event.aggregateId,
            aggregateType = "UserRiskProfile",
            version = event.version,
            eventType = event.eventType,
            payload = payloadJson,
            occurredAt = event.occurredAt
        )
    }
    
    private fun toDomainEvent(
        entity: EventEntity
    ): DomainEvent {
        return try {
            objectMapper.readValue<ActivityEvent>(entity.payload)
        } catch (e: Exception) {
            logger.error("Failed to deserialize event: ${entity.id}, type: ${entity.eventType}", e)
            
            try {
                val payload: Map<String, Any> = objectMapper.readValue(entity.payload)
                GenericDomainEvent(
                    eventId = entity.id ?: throw IllegalStateException("Event entity has no ID"),
                    aggregateId = entity.aggregateId,
                    version = entity.version,
                    eventType = entity.eventType,
                    occurredAt = entity.occurredAt,
                    payloadData = payload
                )
            } catch (fallbackError: Exception) {
                logger.error("Failed to deserialize even as generic event: ${entity.id}", fallbackError)
                GenericDomainEvent(
                    eventId = entity.id ?: throw IllegalStateException("Event entity has no ID"),
                    aggregateId = entity.aggregateId,
                    version = entity.version,
                    eventType = entity.eventType,
                    occurredAt = entity.occurredAt,
                    payloadData = emptyMap()
                )
            }
        }
    }
}

private data class GenericDomainEvent(
    override val eventId: UUID,
    override val aggregateId: String,
    override val version: Long,
    override val eventType: String,
    override val occurredAt: Instant,
    private val payloadData: Map<String, Any>
) : DomainEvent {
    override fun payload(): Map<String, Any> = payloadData
}
