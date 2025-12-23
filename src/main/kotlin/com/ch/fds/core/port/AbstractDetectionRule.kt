package com.ch.fds.core.port

import com.ch.fds.core.model.DetectionResult
import com.ch.fds.core.model.UserActivity
import com.ch.fds.core.model.UserRiskProfile

abstract class AbstractDetectionRule<T : UserActivity> (
    override val ruleId: String,
    override val ruleName: String,
    override val supportedActivityType: String
) : DetectionRule {
    
    final override suspend fun evaluate(
        activity: UserActivity,
        profile: UserRiskProfile
    ): DetectionResult {

        @Suppress("UNCHECKED_CAST")
        val typedActivity = activity as? T
            ?: return DetectionResult.normal(
                ruleId = ruleId,
                ruleName = ruleName,
                reason = "Activity type not supported by this rule"
            )
        
        return doEvaluate(
            activity = typedActivity,
            profile = profile
        )
    }
    
    protected abstract suspend fun doEvaluate(
        activity: T,
        profile: UserRiskProfile
    ): DetectionResult
}