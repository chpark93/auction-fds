package com.ch.fds.domains.payment

import com.ch.fds.core.model.DetectionResult
import com.ch.fds.core.model.PaymentActivity
import com.ch.fds.core.model.RiskScore
import com.ch.fds.core.model.UserRiskProfile
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class StolenCardRule : PaymentRule(
    ruleId = "PAYMENT_STOLEN_CARD",
    ruleName = "Stolen Card Detection"
) {
    private val logger = LoggerFactory.getLogger(StolenCardRule::class.java)
    
    companion object {
        // 10분 내 5건 이상 결제: CRITICAL 리스크
        private const val CRITICAL_PAYMENT_COUNT = 5
        private val CRITICAL_TIME_WINDOW = Duration.ofMinutes(10)

        // 30분 내 5건 이상 결제: HIGH 리스크
        private const val HIGH_PAYMENT_COUNT = 5
        private val HIGH_TIME_WINDOW = Duration.ofMinutes(30)

        // 1시간 내 7건 이상 결제: MEDIUM 리스크
        private const val MEDIUM_PAYMENT_COUNT = 7
        private val MEDIUM_TIME_WINDOW = Duration.ofHours(1)
    }
    
    override suspend fun doEvaluate(
        activity: PaymentActivity,
        profile: UserRiskProfile
    ): DetectionResult {
        val recentPayments = profile.recentPayments
        
        if (recentPayments.isEmpty()) {
            return DetectionResult.normal(
                ruleId = ruleId,
                ruleName = ruleName,
                reason = "First payment for user"
            )
        }
        
        logger.debug(
            "Evaluating stolen card: userId={}, recentPaymentCount={}",
            activity.userId,
            recentPayments.size
        )
        
        // 전체 결제 내역
        val allPayments = recentPayments + activity
        
        // 각 시간 윈도우별 결제 횟수 계산
        val paymentsIn10Min = countPaymentsInWindow(
            payments = allPayments,
            referenceTime = activity.occurredAt,
            window = CRITICAL_TIME_WINDOW
        )
        val paymentsIn30Min = countPaymentsInWindow(
            payments = allPayments,
            referenceTime = activity.occurredAt,
            window = HIGH_TIME_WINDOW
        )
        val paymentsIn1Hour = countPaymentsInWindow(
            payments = allPayments,
            referenceTime = activity.occurredAt,
            window = MEDIUM_TIME_WINDOW
        )
        
        return when {
            paymentsIn10Min >= CRITICAL_PAYMENT_COUNT -> {
                DetectionResult.fraud(
                    ruleId = ruleId,
                    ruleName = ruleName,
                    riskScore = RiskScore.CRITICAL,
                    reason = "Abnormally high payment frequency: $paymentsIn10Min payments in 10 minutes",
                    metadata = mapOf(
                        "paymentCountIn10Min" to paymentsIn10Min,
                        "cardLastFour" to activity.cardLastFour,
                        "amount" to activity.amount.toString()
                    )
                )
            }
            
            paymentsIn30Min >= HIGH_PAYMENT_COUNT -> {
                DetectionResult.fraud(
                    ruleId = ruleId,
                    ruleName = ruleName,
                    riskScore = RiskScore.HIGH,
                    reason = "High payment frequency: $paymentsIn30Min payments in 30 minutes",
                    metadata = mapOf(
                        "paymentCountIn30Min" to paymentsIn30Min,
                        "cardLastFour" to activity.cardLastFour
                    )
                )
            }
            
            paymentsIn1Hour >= MEDIUM_PAYMENT_COUNT -> {
                DetectionResult.fraud(
                    ruleId = ruleId,
                    ruleName = ruleName,
                    riskScore = RiskScore.MEDIUM,
                    reason = "Elevated payment frequency: $paymentsIn1Hour payments in 1 hour",
                    metadata = mapOf(
                        "paymentCountIn1Hour" to paymentsIn1Hour,
                        "cardLastFour" to activity.cardLastFour
                    )
                )
            }
            
            else -> {
                DetectionResult.normal(
                    ruleId = ruleId,
                    ruleName = ruleName,
                    reason = "Payment frequency is within normal range"
                )
            }
        }
    }
    
    private fun countPaymentsInWindow(
        payments: List<PaymentActivity>,
        referenceTime: Instant,
        window: Duration
    ): Int {
        val windowStart = referenceTime.minus(window)

        return payments.count { it.occurredAt.isAfter(windowStart) }
    }
}

