package com.ch.fds.core.service

import com.ch.fds.core.model.*
import com.ch.fds.core.port.DetectionRule
import com.ch.fds.core.port.RiskPolicy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

class RiskAnalysisService(
    private val rules: List<DetectionRule>,
    private val riskPolicy: RiskPolicy
) {
    private val logger = LoggerFactory.getLogger(RiskAnalysisService::class.java)
    
    suspend fun analyze(
        activity: UserActivity,
        profile: UserRiskProfile
    ): AggregatedDetectionResult {
        logger.debug("Analyzing activity: type={}, userId={}", activity.activityType, activity.userId)
        
        // 적용 가능한 규칙 필터링
        val applicableRules = rules.filter { 
            it.enabled && it.supports(activityType = activity.activityType)
        }
        
        if (applicableRules.isEmpty()) {
            logger.warn("No applicable rules found for activity type: {}", activity.activityType)

            return createEmptyResult(
                activity = activity
            )
        }
        
        logger.debug("Found {} applicable rules for activity type: {}", applicableRules.size, activity.activityType)
        
        // 모든 규칙 병렬 실행
        val detectionResults = evaluateRules(
            rules = applicableRules,
            activity = activity,
            profile = profile
        )
        
        // 결과 집계
        val totalRiskScore = riskPolicy.aggregateScores(
            scores = detectionResults.map { it.riskScore }
        )
        val decision = riskPolicy.decide(
            results = detectionResults
        )
        
        logger.info(
            "Analysis complete: userId={}, activityType={}, totalRiskScore={}, decision={}",
            activity.userId,
            activity.activityType,
            totalRiskScore.value,
            decision
        )
        
        return AggregatedDetectionResult(
            userId = activity.userId,
            activityId = activity.activityId,
            activityType = activity.activityType,
            totalRiskScore = totalRiskScore,
            detectionResults = detectionResults,
            decision = decision
        )
    }
    
    private suspend fun evaluateRules(
        rules: List<DetectionRule>,
        activity: UserActivity,
        profile: UserRiskProfile
    ): List<DetectionResult> = coroutineScope {
        rules.map { rule ->
            async {
                try {
                    logger.debug("Evaluating rule: {}", rule.ruleName)

                    rule.evaluate(
                        activity = activity,
                        profile = profile
                    )
                } catch (e: Exception) {
                    logger.error("Error evaluating rule: ${rule.ruleName}", e)

                    DetectionResult.normal(
                        ruleId = rule.ruleId,
                        ruleName = rule.ruleName,
                        reason = "Rule evaluation failed: ${e.message}"
                    )
                }
            }
        }.awaitAll()
    }
    
    private fun createEmptyResult(
        activity: UserActivity
    ): AggregatedDetectionResult {
        return AggregatedDetectionResult(
            userId = activity.userId,
            activityId = activity.activityId,
            activityType = activity.activityType,
            totalRiskScore = RiskScore.ZERO,
            detectionResults = emptyList(),
            decision = FraudDecision.APPROVE
        )
    }
}

