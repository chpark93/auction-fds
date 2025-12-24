package com.ch.fds.infrastructure.config

import com.ch.fds.core.port.DefaultRiskPolicy
import com.ch.fds.core.port.DetectionRule
import com.ch.fds.core.port.ModelInferencePort
import com.ch.fds.core.port.RiskPolicy
import com.ch.fds.core.service.RiskAnalysisService
import com.ch.fds.infrastructure.service.FastBlacklistService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FdsConfig {
    
    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    
    @Bean
    fun riskPolicy(): RiskPolicy {
        return DefaultRiskPolicy()
    }
    
    @Bean
    fun riskAnalysisService(
        rules: List<DetectionRule>,
        riskPolicy: RiskPolicy,
        blacklistService: FastBlacklistService?,
        mlScoringService: ModelInferencePort?
        /*@Autowired(required = false) blacklistService: FastBlacklistService?,
        @Autowired(required = false) mlScoringService: ModelInferencePort?*/
    ): RiskAnalysisService {
        return RiskAnalysisService(
            rules = rules,
            riskPolicy = riskPolicy,
            blacklistService = blacklistService,
            mlScoringService = mlScoringService
        )
    }
}

