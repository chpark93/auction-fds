package com.ch.fds.domains.payment

import com.ch.fds.core.model.PaymentActivity
import com.ch.fds.core.port.AbstractDetectionRule

abstract class PaymentRule(
    ruleId: String,
    ruleName: String
) : AbstractDetectionRule<PaymentActivity>(
    ruleId = ruleId,
    ruleName = ruleName,
    supportedActivityType = "PAYMENT"
)

