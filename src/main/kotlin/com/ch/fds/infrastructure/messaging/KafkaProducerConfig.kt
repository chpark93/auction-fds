package com.ch.fds.infrastructure.messaging

import com.ch.fds.infrastructure.messaging.dto.FraudAlertEvent
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
@ConditionalOnProperty(
    prefix = "fds.kafka",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class KafkaProducerConfig {
    
    @Value("\${spring.kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String
    
    @Bean
    fun producerFactory(): ProducerFactory<String, FraudAlertEvent> {
        val config = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all", // 모든 replica가 확인할 때까지 대기
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 1 // 순서 보장
        )
        
        return DefaultKafkaProducerFactory(config)
    }
    
    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, FraudAlertEvent> {
        return KafkaTemplate(producerFactory())
    }
}

