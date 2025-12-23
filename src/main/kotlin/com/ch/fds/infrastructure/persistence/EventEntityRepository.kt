package com.ch.fds.infrastructure.persistence

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface EventEntityRepository : CoroutineCrudRepository<EventEntity, UUID> {
    
    @Query("SELECT * FROM domain_events WHERE aggregate_id = :aggregateId ORDER BY version ASC")
    fun findByAggregateIdOrderByVersionAsc(
        aggregateId: String
    ): Flow<EventEntity>
    
    @Query("SELECT * FROM domain_events WHERE aggregate_id = :aggregateId AND version > :afterVersion ORDER BY version ASC")
    fun findByAggregateIdAndVersionGreaterThanOrderByVersionAsc(
        aggregateId: String,
        afterVersion: Long
    ): Flow<EventEntity>
    
    @Query("SELECT * FROM domain_events WHERE event_type = :eventType ORDER BY occurred_at DESC")
    fun findByEventTypeOrderByOccurredAtDesc(
        eventType: String
    ): Flow<EventEntity>
    
    @Query("SELECT COALESCE(MAX(version), 0) FROM domain_events WHERE aggregate_id = :aggregateId")
    suspend fun findMaxVersionByAggregateId(
        aggregateId: String
    ): Long
}

