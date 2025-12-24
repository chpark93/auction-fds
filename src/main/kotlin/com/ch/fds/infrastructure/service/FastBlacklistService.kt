package com.ch.fds.infrastructure.service

import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service
@Suppress("UnstableApiUsage")
class FastBlacklistService {
    
    private val logger = LoggerFactory.getLogger(FastBlacklistService::class.java)
    
    companion object {
        // Bloom Filter 설정
        // 100만개 항목
        private const val EXPECTED_INSERTIONS = 1_000_000L
        // 0.01% 오탐률
        private const val FALSE_POSITIVE_RATE = 0.0001
        
        // 초기 블랙리스트 (예시)
        private val INITIAL_BLACKLIST = setOf(
            "malicious_user_001",
            "malicious_user_002",
            "fraud_bot_123",
            "192.168.1.100", // 악성 IP
            "10.0.0.50"
        )
    }
    
    @Volatile
    private var bloomFilter: BloomFilter<String> = createBloomFilter()
    
    private val lock = ReentrantReadWriteLock()
    
    /**
     * 통계 정보
     */
    @Volatile
    private var totalChecks = 0L
    
    @Volatile
    private var potentialMatches = 0L
    
    init {
        // 초기 블랙리스트 로드
        INITIAL_BLACKLIST.forEach { addToBlacklist(it) }
        
        logger.info(
            "✅ FastBlacklistService initialized: expectedInsertions={}, fpr={}%, initialSize={}",
            EXPECTED_INSERTIONS,
            FALSE_POSITIVE_RATE * 100,
            INITIAL_BLACKLIST.size
        )
    }
    
    /**
     * 블랙리스트에 포함될 가능성이 있는지 검사.
     * - false: 100% 블랙리스트에 없음 (DB 조회 불필요)
     * - true: 블랙리스트에 있을 가능성 있음 (DB 조회 필요)
     */
    fun isMightContain(
        value: String
    ): Boolean {
        totalChecks++
        
        val result = lock.read {
            bloomFilter.mightContain(value)
        }
        
        if (result) {
            potentialMatches++
            logger.debug("⚠️ Potential blacklist match: value={}", value)
        }
        
        return result
    }
    
    /**
     * 블랙리스트에 값을 추가.
     * - Bloom Filter는 삭제를 지원하지 않는다.
     * - 용량 초과 시 FPR이 증가할 수 있으므로, 주기적으로 재구축 필요.
     */
    fun addToBlacklist(
        value: String
    ) {
        lock.write {
            val wasAdded = !bloomFilter.mightContain(value)
            bloomFilter.put(value)
            
            if (wasAdded) {
                logger.info("🚫 Added to blacklist: value={}", value)
            }
        }
    }
    
    /**
     * 여러 값을 한 번에 추가.
     */
    fun addAllToBlacklist(
        values: Collection<String>
    ) {
        lock.write {
            values.forEach { value ->
                bloomFilter.put(value)
            }
            
            logger.info("🚫 Bulk added to blacklist: count={}", values.size)
        }
    }
    
    /**
     * Bloom Filter를 새로 생성하고 기존 데이터를 복사.
     * - 예상 용량 초과 시
     * - FPR이 너무 높아진 경우
     * - 블랙리스트를 외부에서 다시 로드할 때
     */
    fun rebuild(
        expectedInsertions: Long = EXPECTED_INSERTIONS,
        fpr: Double = FALSE_POSITIVE_RATE
    ) {
        lock.write {
            logger.info("🔄 Rebuilding Bloom Filter: expectedInsertions={}, fpr={}", expectedInsertions, fpr)
            
            // 새 Bloom Filter 생성
            bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions,
                fpr
            )
            
            // 초기 블랙리스트 재등록
            INITIAL_BLACKLIST.forEach { bloomFilter.put(it) }
            
            logger.info("✅ Bloom Filter rebuilt successfully")
        }
    }
    
    /**
     * 통계 정보를 반환.
     */
    fun getStats(): BlacklistStats {
        return lock.read {
            BlacklistStats(
                totalChecks = totalChecks,
                potentialMatches = potentialMatches,
                estimatedFpr = if (totalChecks > 0) {
                    (potentialMatches.toDouble() / totalChecks) * 100
                } else {
                    0.0
                },
                approximateElementCount = bloomFilter.approximateElementCount()
            )
        }
    }
    
    /**
     * 통계 정보 초기화.
     */
    fun resetStats() {
        totalChecks = 0
        potentialMatches = 0
        logger.info("📊 Blacklist stats reset")
    }
    
    /**
     * Bloom Filter 생성 헬퍼 함수.
     */
    private fun createBloomFilter(): BloomFilter<String> {
        return BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            EXPECTED_INSERTIONS,
            FALSE_POSITIVE_RATE
        )
    }
}

/**
 * 블랙리스트 통계 정보.
 */
data class BlacklistStats(
    val totalChecks: Long,
    val potentialMatches: Long,
    val estimatedFpr: Double,
    val approximateElementCount: Long
) {
    override fun toString(): String {
        return """
            BlacklistStats(
              totalChecks=$totalChecks,
              potentialMatches=$potentialMatches,
              estimatedFPR=${String.format("%.4f", estimatedFpr)}%,
              approximateElementCount=$approximateElementCount
            )
        """.trimIndent()
    }
}

