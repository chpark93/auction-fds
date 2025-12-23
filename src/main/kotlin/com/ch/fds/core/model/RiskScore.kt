package com.ch.fds.core.model

data class RiskScore(
    val value: Int
) : Comparable<RiskScore> {

    init {
        require(value in 0..100) { "Risk score must be between 0 and 100, but was $value" }
    }

    companion object {
        val ZERO = RiskScore(0)
        val LOW = RiskScore(20)
        val MEDIUM = RiskScore(50)
        val HIGH = RiskScore(70)
        val CRITICAL = RiskScore(90)
    }
    
    val level: RiskLevel
        get() = when (value) {
            in 0..30 -> RiskLevel.LOW
            in 31..60 -> RiskLevel.MEDIUM
            in 61..80 -> RiskLevel.HIGH
            else -> RiskLevel.CRITICAL
        }
    
    operator fun plus(
        other: RiskScore
    ): RiskScore {
        return RiskScore((value + other.value).coerceIn(0, 100))
    }
    
    override fun compareTo(
        other: RiskScore
    ): Int {
        return value.compareTo(other.value)
    }
}


