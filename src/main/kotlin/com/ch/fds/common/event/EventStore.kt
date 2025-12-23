package com.ch.fds.common.event

import kotlinx.coroutines.flow.Flow

interface EventStore {

    suspend fun save(
        event: DomainEvent
    ): DomainEvent
    
    suspend fun saveAll(
        events: Flow<DomainEvent>
    ): Long
    
    fun load(
        aggregateId: String
    ): Flow<DomainEvent>
    
    fun loadAfterVersion(
        aggregateId: String,
        afterVersion: Long
    ): Flow<DomainEvent>
    
    fun findByEventType(
        eventType: String
    ): Flow<DomainEvent>
}

