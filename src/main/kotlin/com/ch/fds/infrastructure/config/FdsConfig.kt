package com.ch.fds.infrastructure.config

import com.ch.fds.core.port.DefaultRiskPolicy
import com.ch.fds.core.port.DetectionRule
import com.ch.fds.core.port.RiskPolicy
import com.ch.fds.core.service.RiskAnalysisService
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
        riskPolicy: RiskPolicy
    ): RiskAnalysisService {
        return RiskAnalysisService(
            rules = rules,
            riskPolicy = riskPolicy
        )
    }
}

