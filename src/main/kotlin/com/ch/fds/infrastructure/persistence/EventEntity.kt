package com.ch.fds.infrastructure.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.*

@Table("domain_events")
data class EventEntity(
    @Id
    val id: UUID? = null,
    
    @Column("aggregate_id")
    val aggregateId: String,
    
    @Column("aggregate_type")
    val aggregateType: String,
    
    @Column("version")
    val version: Long,
    
    @Column("event_type")
    val eventType: String,
    
    @Column("payload")
    val payload: String,
    
    @Column("occurred_at")
    val occurredAt: Instant,
    
    @Column("created_at")
    val createdAt: Instant = Instant.now()
)
