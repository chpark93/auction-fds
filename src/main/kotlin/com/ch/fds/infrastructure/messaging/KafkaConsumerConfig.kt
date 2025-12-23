package com.ch.fds.infrastructure.messaging

import com.ch.fds.infrastructure.messaging.dto.BidPlacedEvent
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonDeserializer

@Configuration
@EnableKafka
@ConditionalOnProperty(
    prefix = "fds.kafka",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class KafkaConsumerConfig {
    
    @Value("\${spring.kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String
    
    @Value("\${spring.kafka.consumer.group-id}")
    private lateinit var groupId: String
    
    @Bean
    fun consumerFactory(): ConsumerFactory<String, BidPlacedEvent> {
        val config = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
            ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS to JsonDeserializer::class.java,
            JsonDeserializer.VALUE_DEFAULT_TYPE to BidPlacedEvent::class.java.name,
            JsonDeserializer.TRUSTED_PACKAGES to "*",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false // 수동 커밋 사용
        )
        
        return DefaultKafkaConsumerFactory(
            config,
            StringDeserializer(),
            JsonDeserializer(BidPlacedEvent::class.java).apply {
                addTrustedPackages("*")
            }
        )
    }
    
    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, BidPlacedEvent> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, BidPlacedEvent>()
        factory.consumerFactory = consumerFactory()
        
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        
        factory.setConcurrency(3)
        
        return factory
    }
}
