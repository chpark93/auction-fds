package com.ch.fds.application.api.dto

import com.ch.fds.core.engine.InferenceStats
import com.ch.fds.infrastructure.service.BlacklistStats

object FdsDtos {
    data class HybridSystemStats(
        val blacklistStats: BlacklistStats?,
        val inferenceStats: InferenceStats?,
        val systemHealth: SystemHealth
    )

    data class SystemHealth(
        val blacklistEnabled: Boolean,
        val aiModelEnabled: Boolean,
        val aiModelLoaded: Boolean
    ) {
        val overallHealth: String
            get() = when {
                blacklistEnabled && aiModelEnabled && aiModelLoaded -> "EXCELLENT"
                blacklistEnabled && aiModelEnabled -> "GOOD (AI model not loaded)"
                blacklistEnabled || aiModelEnabled -> "DEGRADED (partial features)"
                else -> "BASIC (rules only)"
            }
    }

    data class AddToBlacklistResponse(
        val success: Boolean,
        val message: String
    )

    data class BulkBlacklistRequest(
        val userIds: List<String>
    )

    data class BlacklistCheckResponse(
        val userId: String,
        val mightContain: Boolean,
        val message: String
    )

    data class RebuildResponse(
        val success: Boolean,
        val message: String
    )

    data class ResetStatsResponse(
        val success: Boolean,
        val message: String
    )
}