package com.portfolio.fraudshield.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.fraudshield.domain.TransactionEvent;
import com.portfolio.fraudshield.domain.TransactionStatus;
import com.portfolio.fraudshield.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end check of the Week 1 pipeline against real PostgreSQL and Kafka: a transaction is stored,
 * published once to {@code transactions.v1} keyed by customerId, and a replay does not publish again.
 * Requires Docker.
 */
@SpringBootTest
@Testcontainers
class TransactionIngestIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    TransactionIngestService service;

    @Autowired
    TransactionRepository repository;

    @Value("${fraudshield.topics.transactions}")
    String topic;

    private static TransactionEvent event(String id, String customer) {
        return new TransactionEvent(id, customer, "acct-" + customer, "merchant-1",
                new BigDecimal("19.99"), "USD", TransactionStatus.APPROVED,
                Instant.parse("2026-01-01T12:00:00Z"), 37.77, -122.42, "US");
    }

    @Test
    void storesTransactionAndPublishesItToKafkaKeyedByCustomer() {
        String id = "it-" + UUID.randomUUID();
        IngestResult result = service.ingest(event(id, "customer-kafka"));

        assertThat(result.duplicate()).isFalse();
        assertThat(repository.findById(id)).isPresent();

        List<ConsumerRecord<String, String>> messages = collect(id, Duration.ofSeconds(30), 1);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).key()).isEqualTo("customer-kafka");
        assertThat(messages.get(0).value()).contains("\"eventId\":\"" + id + "\"");
    }

    @Test
    void replayingTheSameEventIsIdempotentAndPublishesOnlyOnce() {
        String id = "it-" + UUID.randomUUID();

        IngestResult first = service.ingest(event(id, "customer-replay"));
        IngestResult second = service.ingest(event(id, "customer-replay"));

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(repository.findById(id)).isPresent();

        // Wait the full window so a (wrong) second message would have time to arrive.
        List<ConsumerRecord<String, String>> messages = collect(id, Duration.ofSeconds(10), Integer.MAX_VALUE);

        assertThat(messages).hasSize(1);
    }

    /** Reads the topic from the beginning and returns the records whose payload mentions {@code eventId}. */
    private List<ConsumerRecord<String, String>> collect(String eventId, Duration maxWait, int stopAfter) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        List<ConsumerRecord<String, String>> matches = new ArrayList<>();
        long deadline = System.nanoTime() + maxWait.toNanos();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            while (System.nanoTime() < deadline && matches.size() < stopAfter) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.value().contains(eventId)) {
                        matches.add(record);
                    }
                }
            }
        }
        return matches;
    }
}
