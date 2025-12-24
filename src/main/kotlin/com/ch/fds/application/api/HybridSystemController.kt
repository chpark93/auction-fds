package com.ch.fds.application.api

import com.ch.fds.application.api.dto.FdsDtos
import com.ch.fds.core.engine.InferenceStats
import com.ch.fds.core.engine.MlScoringService
import com.ch.fds.infrastructure.service.BlacklistStats
import com.ch.fds.infrastructure.service.FastBlacklistService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/hybrid")
class HybridSystemController(
    private val blacklistService: FastBlacklistService?,
    private val mlScoringService: MlScoringService?
) {
    
    /**
     * 전체 시스템 통계 조회
     */
    @GetMapping("/stats")
    suspend fun getSystemStats(): FdsDtos.HybridSystemStats {
        return FdsDtos.HybridSystemStats(
            blacklistStats = blacklistService?.getStats(),
            inferenceStats = mlScoringService?.getStats(),
            systemHealth = FdsDtos.SystemHealth(
                blacklistEnabled = blacklistService != null,
                aiModelEnabled = mlScoringService != null,
                aiModelLoaded = mlScoringService?.isModelLoaded ?: false
            )
        )
    }
    
    /**
     * Bloom Filter 통계 조회
     */
    @GetMapping("/blacklist/stats")
    fun getBlacklistStats(): BlacklistStats? {
        return blacklistService?.getStats()
    }
    
    /**
     * AI 추론 통계 조회
     */
    @GetMapping("/inference/stats")
    fun getInferenceStats(): InferenceStats? {
        return mlScoringService?.getStats()
    }
    
    /**
     * 블랙리스트에 사용자 추가
     */
    @PostMapping("/blacklist/add")
    fun addToBlacklist(
        @RequestParam userId: String
    ): FdsDtos.AddToBlacklistResponse {
        if (blacklistService == null) {
            return FdsDtos.AddToBlacklistResponse(
                success = false,
                message = "Blacklist service is not enabled"
            )
        }
        
        blacklistService.addToBlacklist(userId)
        
        return FdsDtos.AddToBlacklistResponse(
            success = true,
            message = "User $userId added to blacklist"
        )
    }
    
    /**
     * 블랙리스트 일괄 추가
     */
    @PostMapping("/blacklist/add-bulk")
    fun addBulkToBlacklist(
        @RequestBody request: FdsDtos.BulkBlacklistRequest
    ): FdsDtos.AddToBlacklistResponse {
        if (blacklistService == null) {
            return FdsDtos.AddToBlacklistResponse(
                success = false,
                message = "Blacklist service is not enabled"
            )
        }
        
        blacklistService.addAllToBlacklist(request.userIds)
        
        return FdsDtos.AddToBlacklistResponse(
            success = true,
            message = "${request.userIds.size} users added to blacklist"
        )
    }
    
    /**
     * 블랙리스트 검사
     */
    @GetMapping("/blacklist/check")
    fun checkBlacklist(
        @RequestParam userId: String
    ): FdsDtos.BlacklistCheckResponse {
        if (blacklistService == null) {
            return FdsDtos.BlacklistCheckResponse(
                userId = userId,
                mightContain = false,
                message = "Blacklist service is not enabled"
            )
        }
        
        val mightContain = blacklistService.isMightContain(userId)
        
        return FdsDtos.BlacklistCheckResponse(
            userId = userId,
            mightContain = mightContain,
            message = if (mightContain) {
                "User might be in blacklist (requires DB confirmation)"
            } else {
                "User is definitely NOT in blacklist"
            }
        )
    }
    
    /**
     * Bloom Filter 재구축
     */
    @PostMapping("/blacklist/rebuild")
    fun rebuildBloomFilter(): FdsDtos.RebuildResponse {
        if (blacklistService == null) {
            return FdsDtos.RebuildResponse(
                success = false,
                message = "Blacklist service is not enabled"
            )
        }
        
        blacklistService.rebuild()
        
        return FdsDtos.RebuildResponse(
            success = true,
            message = "Bloom filter rebuilt successfully"
        )
    }
    
    /**
     * 통계 초기화
     */
    @PostMapping("/stats/reset")
    fun resetStats(): FdsDtos.ResetStatsResponse {
        blacklistService?.resetStats()
        mlScoringService?.resetStats()
        
        return FdsDtos.ResetStatsResponse(
            success = true,
            message = "All statistics reset"
        )
    }
}

