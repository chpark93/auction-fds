package com.ch.fds.core.model

import java.time.Instant

data class TransactionFeatures(
    val userId: String,
    val accountAgeSeconds: Long = 0L,

    val activityType: Int,
    val amount: Double,
    val timestamp: Long = Instant.now().epochSecond,
    
    val activityCountLast1Min: Int = 0,
    val activityCountLast5Min: Int = 0,
    val activityCountLast1Hour: Int = 0,
    val hourOfDay: Int = 0,
    val dayOfWeek: Int = 1,
    
    val currentRiskScore: Int = 0,
    val previousRiskScore: Int = 0,
    val totalActivityCount: Int = 0,
    
    // Auction
    val timeUntilAuctionEnd: Long? = null,
    val samAuctionBidCount: Int? = null,

    // Payment
    val paymentMethodType: Int? = null,
    val amountRatioToPrevious: Double? = null
) {
    companion object {
        // Feature 벡터 사이즈
        const val FEATURE_VECTOR_SIZE = 20
        
        // Activity Type 인코딩
        const val ACTIVITY_TYPE_BID = 1
        const val ACTIVITY_TYPE_PAYMENT = 2
        const val ACTIVITY_TYPE_TRADE = 3
        
        // Payment Method Type 인코딩
        const val PAYMENT_METHOD_CARD = 1
        const val PAYMENT_METHOD_BANK_TRANSFER = 2
        const val PAYMENT_METHOD_CRYPTO = 3
    }
    
    fun toFloatArray(): FloatArray {
        return floatArrayOf(
            // 기본 정보
            kotlin.math.ln(accountAgeSeconds.toDouble() + 1.0).toFloat(),
            activityType.toFloat(),
            kotlin.math.ln(amount + 1.0).toFloat(),
            (timestamp / 1_000_000_000.0).toFloat(),
            
            // 시간 기반 패턴
            activityCountLast1Min.toFloat(),
            activityCountLast5Min.toFloat(),
            activityCountLast1Hour.toFloat(),
            
            // 시간대 정보
            (hourOfDay / 24.0).toFloat(),
            (dayOfWeek / 7.0).toFloat(),
            
            // 리스크 프로필
            (currentRiskScore / 100.0).toFloat(),
            (previousRiskScore / 100.0).toFloat(),
            kotlin.math.ln(totalActivityCount.toDouble() + 1.0).toFloat(),
            
            // Auction 특성
            (timeUntilAuctionEnd?.let { kotlin.math.ln(it.toDouble() + 1.0) } ?: 0.0).toFloat(),
            (samAuctionBidCount ?: 0).toFloat(),
            
            // Payment 특성
            (paymentMethodType ?: 0).toFloat(),
            (amountRatioToPrevious ?: 1.0).toFloat(),
            
            // 예약 (추후 확장용)
            0.0f, 0.0f, 0.0f, 0.0f
        )
    }
    
    fun toDebugString(): String {
        return """
            TransactionFeatures(
              userId=$userId,
              activityType=$activityType,
              amount=$amount,
              currentRiskScore=$currentRiskScore,
              activityCountLast1Min=$activityCountLast1Min,
              activityCountLast5Min=$activityCountLast5Min
            )
        """.trimIndent()
    }
}

