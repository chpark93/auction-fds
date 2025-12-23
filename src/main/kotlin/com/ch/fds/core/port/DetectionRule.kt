package com.ch.fds.core.port

import com.ch.fds.core.model.DetectionResult
import com.ch.fds.core.model.UserActivity
import com.ch.fds.core.model.UserRiskProfile

interface DetectionRule {
    val ruleId: String
    val ruleName: String
    val supportedActivityType: String
    val enabled: Boolean get() = true
    
    suspend fun evaluate(
        activity: UserActivity,
        profile: UserRiskProfile
    ): DetectionResult
    
    fun supports(
        activityType: String
    ): Boolean {
        return supportedActivityType == "*" || supportedActivityType == activityType
    }
}
