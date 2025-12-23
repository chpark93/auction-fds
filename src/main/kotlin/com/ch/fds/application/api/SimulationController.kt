package com.ch.fds.application.api

import com.ch.fds.application.port.`in`.IngestActivityUseCase
import com.ch.fds.core.model.BidActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.system.measureTimeMillis

@RestController
@RequestMapping("/simulate")
class SimulationController(
    private val ingestActivityUseCase: IngestActivityUseCase
) {
    private val logger = LoggerFactory.getLogger(SimulationController::class.java)
    
    /**
     * 공격 시뮬레이션
     * Scenario 1: 악의적 사용자가 짧은 시간 내에 여러 번 입찰
     * Scenario 2: 정상 사용자들의 백그라운드 트래픽
     */
    @PostMapping("/attack")
    suspend fun simulateAttack(
        @RequestParam(defaultValue = "50") attackBidCount: Int,
        @RequestParam(defaultValue = "100") normalUserCount: Int
    ): SimulationResult = coroutineScope {
        logger.info("🎯 Starting attack simulation: attackBids={}, normalUsers={}", attackBidCount, normalUserCount)
        
        val startTime = Instant.now()
        var attackSuccess = 0
        var normalSuccess = 0
        var errors = 0
        
        val executionTime = measureTimeMillis {
            try {
                // Scenario 1: 공격자 - 동일 경매에 빠르게 여러 번 입찰
                val attackerJob = async {
                    simulateAttacker(
                        userId = "joker_123",
                        auctionId = "item_99",
                        bidCount = attackBidCount
                    )
                }
                
                // Scenario 2: 정상 사용자들 - 다양한 경매에 정상적으로 입찰
                val normalJobs = (1..normalUserCount).map { userIndex ->
                    async {
                        simulateNormalUser(
                            userId = "user_$userIndex",
                            auctionId = "auction_${Random.nextInt(1, 20)}"
                        )
                    }
                }
                
                // 모든 작업 완료 대기
                val attackResult = attackerJob.await()
                val normalResults = normalJobs.awaitAll()
                
                attackSuccess = attackResult.successCount
                errors += attackResult.errorCount
                
                normalResults.forEach { result ->
                    normalSuccess += result.successCount
                    errors += result.errorCount
                }
                
            } catch (e: Exception) {
                logger.error("Error during simulation", e)
                errors++
            }
        }
        
        val endTime = Instant.now()
        
        val result = SimulationResult(
            totalEvents = attackBidCount + normalUserCount,
            attackBids = attackBidCount,
            normalBids = normalUserCount,
            successCount = attackSuccess + normalSuccess,
            errorCount = errors,
            executionTimeMs = executionTime,
            startTime = startTime,
            endTime = endTime,
            message = "✅ Simulation completed. Check logs for FRAUD ALERT messages."
        )
        
        logger.info("📊 Simulation result: {}", result)
        
        return@coroutineScope result
    }
    
    /**
     * 공격자 시뮬레이션
     * 동일 경매에 짧은 시간 내 여러 번 입찰
     * BidSnipingRule 트리거.
     */
    private suspend fun simulateAttacker(
        userId: String,
        auctionId: String,
        bidCount: Int
    ): UserSimulationResult = coroutineScope {
        logger.warn("🔴 Simulating ATTACKER: userId={}, auctionId={}, bids={}", userId, auctionId, bidCount)
        
        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        
        // 짧은 시간 내 여러 번 입찰 (10초 내 50회 = CRITICAL 트리거)
        val jobs = (0 until bidCount).map { index ->
            async {
                try {
                    val activity = BidActivity(
                        activityId = UUID.randomUUID(),
                        userId = userId,
                        occurredAt = Instant.now(),
                        auctionId = auctionId,
                        bidAmount = BigDecimal("${10000 + index * 100}"),
                        previousBidAmount = if (index > 0) BigDecimal("${10000 + (index - 1) * 100}") else null,
                        timeUntilAuctionEnd = 600L, // 10분 남음
                        bidCount = index + 1
                    )
                    
                    ingestActivityUseCase.ingest(
                        activity = activity
                    )

                    successCount.incrementAndGet()
                    
                    delay(10L)
                    
                } catch (e: Exception) {
                    logger.error("❌ Error processing attacker bid: userId=$userId, index=$index", e)
                    errorCount.incrementAndGet()
                }
            }
        }
        
        jobs.forEach { it.await() }
        
        return@coroutineScope UserSimulationResult(
            successCount = successCount.get(),
            errorCount = errorCount.get()
        )
    }
    
    /**
     * 정상 사용자 시뮬레이션
     * 다양한 경매에 정상적인 간격 입찰.
     */
    private suspend fun simulateNormalUser(
        userId: String,
        auctionId: String
    ): UserSimulationResult {
        return try {
            val activity = BidActivity(
                activityId = UUID.randomUUID(),
                userId = userId,
                occurredAt = Instant.now(),
                auctionId = auctionId,
                bidAmount = BigDecimal("${Random.nextInt(5000, 50000)}"),
                previousBidAmount = null,
                timeUntilAuctionEnd = Random.nextLong(300, 3600),
                bidCount = 1
            )
            
            ingestActivityUseCase.ingest(activity)
            
            delay(Random.nextLong(50, 200))
            
            UserSimulationResult(
                successCount = 1,
                errorCount = 0
            )
            
        } catch (e: Exception) {
            logger.error("❌ Error processing normal user bid: userId=$userId, auctionId=$auctionId", e)
            UserSimulationResult(
                successCount = 0,
                errorCount = 1
            )
        }
    }
}

data class SimulationResult(
    val totalEvents: Int,
    val attackBids: Int,
    val normalBids: Int,
    val successCount: Int,
    val errorCount: Int,
    val executionTimeMs: Long,
    val startTime: Instant,
    val endTime: Instant,
    val message: String
)

private data class UserSimulationResult(
    val successCount: Int,
    val errorCount: Int
)

