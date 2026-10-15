package com.orderflow.orderservice.config;

import com.orderflow.orderservice.messaging.OrderEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** The producer owns its topic's shape, so the topic is declared here rather than left to broker defaults. */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(OrderEvent.TOPIC).partitions(3).replicas(1).build();
    }
}
