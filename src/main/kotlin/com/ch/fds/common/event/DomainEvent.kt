package com.ch.fds.common.event

import java.time.Instant
import java.util.*

interface DomainEvent {

    val eventId: UUID
    val aggregateId: String
    val version: Long
    val eventType: String
    val occurredAt: Instant
    
    fun payload(): Map<String, Any>
}

