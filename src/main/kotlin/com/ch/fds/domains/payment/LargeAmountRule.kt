package com.ch.fds.domains.payment

import com.ch.fds.core.model.DetectionResult
import com.ch.fds.core.model.PaymentActivity
import com.ch.fds.core.model.RiskScore
import com.ch.fds.core.model.UserRiskProfile
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class LargeAmountRule : PaymentRule(
    ruleId = "PAYMENT_LARGE_AMOUNT",
    ruleName = "Large Amount Detection"
) {
    private val logger = LoggerFactory.getLogger(LargeAmountRule::class.java)
    
    companion object {
        // 결제 금액이 평균의 5배 이상: HIGH 리스크
        private const val HIGH_MULTIPLIER = 5.0
        // 결제 금액이 평균의 3배 이상: MEDIUM 리스크
        private const val MEDIUM_MULTIPLIER = 3.0
        // 절대 금액이 1,000,000 이상: HIGH 리스크
        private val ABSOLUTE_HIGH_AMOUNT = BigDecimal("1000000")
    }
    
    override suspend fun doEvaluate(
        activity: PaymentActivity,
        profile: UserRiskProfile
    ): DetectionResult {
        val currentAmount = activity.amount
        val recentPayments = profile.recentPayments
        
        logger.debug(
            "Evaluating large amount: userId={}, amount={}, currency={}",
            activity.userId,
            currentAmount,
            activity.currency
        )
        
        // 절대 금액 기준 체크
        if (currentAmount >= ABSOLUTE_HIGH_AMOUNT) {
            return DetectionResult.fraud(
                ruleId = ruleId,
                ruleName = ruleName,
                riskScore = RiskScore.HIGH,
                reason = "Extremely high payment amount: $currentAmount ${activity.currency}",
                metadata = mapOf(
                    "amount" to currentAmount.toString(),
                    "currency" to activity.currency,
                    "threshold" to ABSOLUTE_HIGH_AMOUNT.toString()
                )
            )
        }
        
        // 과거 결제 이력이 없으면 상대 비교 불가
        if (recentPayments.isEmpty()) {
            return if (currentAmount >= BigDecimal("500000")) {
                DetectionResult.fraud(
                    ruleId = ruleId,
                    ruleName = ruleName,
                    riskScore = RiskScore.MEDIUM,
                    reason = "Large first payment: $currentAmount ${activity.currency}",
                    metadata = mapOf(
                        "amount" to currentAmount.toString(),
                        "isFirstPayment" to true
                    )
                )
            } else {
                DetectionResult.normal(
                    ruleId = ruleId,
                    ruleName = ruleName,
                    reason = "First payment, amount within acceptable range"
                )
            }
        }
        
        // 평균 결제 금액 계산
        val averageAmount = calculateAverageAmount(
            payments = recentPayments
        )
        
        if (averageAmount <= BigDecimal.ZERO) {
            return DetectionResult.normal(
                ruleId = ruleId,
                ruleName = ruleName,
                reason = "Unable to calculate average amount"
            )
        }
        
        // 현재 금액과 평균의 비율 계산
        val ratio = currentAmount.divide(averageAmount, 2, java.math.RoundingMode.HALF_UP).toDouble()
        
        return when {
            ratio >= HIGH_MULTIPLIER -> {
                DetectionResult.fraud(
                    ruleId = ruleId,
                    ruleName = ruleName,
                    riskScore = RiskScore.HIGH,
                    reason = "Payment amount is ${ratio}x higher than average (${averageAmount})",
                    metadata = mapOf(
                        "currentAmount" to currentAmount.toString(),
                        "averageAmount" to averageAmount.toString(),
                        "ratio" to ratio
                    )
                )
            }
            
            ratio >= MEDIUM_MULTIPLIER -> {
                DetectionResult.fraud(
                    ruleId = ruleId,
                    ruleName = ruleName,
                    riskScore = RiskScore.MEDIUM,
                    reason = "Payment amount is ${ratio}x higher than average (${averageAmount})",
                    metadata = mapOf(
                        "currentAmount" to currentAmount.toString(),
                        "averageAmount" to averageAmount.toString(),
                        "ratio" to ratio
                    )
                )
            }
            
            else -> {
                DetectionResult.normal(
                    ruleId = ruleId,
                    ruleName = ruleName,
                    reason = "Payment amount is within normal range (${ratio}x average)"
                )
            }
        }
    }
    
    private fun calculateAverageAmount(
        payments: List<PaymentActivity>
    ): BigDecimal {
        if (payments.isEmpty()) {
            return BigDecimal.ZERO
        }
        
        val total = payments.fold(BigDecimal.ZERO) { acc, payment ->
            acc + payment.amount
        }
        
        return total.divide(
            BigDecimal(payments.size),
            2,
            java.math.RoundingMode.HALF_UP
        )
    }
}

