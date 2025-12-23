package com.ch.fds.domains.auction

import com.ch.fds.core.model.BidActivity
import com.ch.fds.core.port.AbstractDetectionRule

abstract class AuctionRule(
    ruleId: String,
    ruleName: String
) : AbstractDetectionRule<BidActivity>(
    ruleId = ruleId,
    ruleName = ruleName,
    supportedActivityType = "BID"
)

