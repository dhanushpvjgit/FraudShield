package com.portfolio.fraudshield.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.fraudshield.domain.TransactionEvent;
import com.portfolio.fraudshield.domain.TransactionRecord;
import com.portfolio.fraudshield.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs against a real PostgreSQL so Flyway's migration and Hibernate's schema validation
 * ({@code ddl-auto: validate}) are exercised for real. Requires Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TransactionRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    TransactionRepository repository;

    private static TransactionEvent event(String id, String customer, String time, boolean fraud) {
        return new TransactionEvent(id, customer, "acct-" + customer, "merchant-1",
                new BigDecimal("12.50"), "USD", TransactionStatus.APPROVED,
                Instant.parse(time), 37.77, -122.42, "US", fraud);
    }

    @Test
    void persistsAndReadsBackEveryField() {
        repository.saveAndFlush(new TransactionRecord(
                event("it-1", "customer-1", "2026-01-01T00:00:00Z", true)));

        TransactionRecord loaded = repository.findById("it-1").orElseThrow();

        assertThat(loaded.getCustomerId()).isEqualTo("customer-1");
        assertThat(loaded.getAmount()).isEqualByComparingTo("12.50");
        assertThat(loaded.getStatus()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(loaded.getEventTime()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(loaded.isLabelledFraud()).isTrue();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.isNew()).isFalse();
    }

    @Test
    void recentTransactionsAreNewestFirstAndScopedToCustomer() {
        repository.save(new TransactionRecord(event("a", "cust-x", "2026-01-01T10:00:00Z", false)));
        repository.save(new TransactionRecord(event("b", "cust-x", "2026-01-01T12:00:00Z", false)));
        repository.save(new TransactionRecord(event("c", "cust-x", "2026-01-01T11:00:00Z", false)));
        repository.save(new TransactionRecord(event("d", "cust-y", "2026-01-01T13:00:00Z", false)));
        repository.flush();

        List<TransactionRecord> recent = repository.findTop100ByCustomerIdOrderByEventTimeDesc("cust-x");

        assertThat(recent).extracting(TransactionRecord::getEventId).containsExactly("b", "c", "a");
    }
}
