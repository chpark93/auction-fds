package com.ch.fds.application.port.`in`

import com.ch.fds.core.model.AggregatedDetectionResult
import com.ch.fds.core.model.UserActivity

interface IngestActivityUseCase {

    suspend fun ingest(
        activity: UserActivity
    ): AggregatedDetectionResult
}

