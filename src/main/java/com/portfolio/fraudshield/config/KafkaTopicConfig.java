package com.portfolio.fraudshield.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /**
     * Three partitions so the stream processor added in Week 2 can scale out.
     * Records are keyed by customerId, so all events of one customer stay in one partition (ordered).
     */
    @Bean
    NewTopic transactionsTopic(@Value("${fraudshield.topics.transactions}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
