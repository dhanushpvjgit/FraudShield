package com.portfolio.fraudshield.service;

import com.portfolio.fraudshield.domain.TransactionEvent;
import com.portfolio.fraudshield.domain.TransactionRecord;
import com.portfolio.fraudshield.metrics.FraudMetrics;
import com.portfolio.fraudshield.repository.TransactionRepository;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Accepts a transaction: stores it in PostgreSQL, then publishes it to Kafka.
 *
 * <h2>Guarantees</h2>
 * <ul>
 *   <li><b>Idempotent by eventId</b>: re-submitting the same event is a no-op that returns the
 *       stored record.</li>
 *   <li><b>No phantom events</b>: the Kafka publish runs only <i>after</i> the database commit, so a
 *       rolled-back transaction is never published.</li>
 *   <li><b>At-least-once, not exactly-once</b>: the database write and the Kafka publish are two
 *       separate systems. If the process dies between commit and publish the event stays in
 *       PostgreSQL but never reaches Kafka. A transactional outbox would close that gap; it is
 *       listed as a known limitation in the README.</li>
 * </ul>
 */
@Service
public class TransactionIngestService {

    private static final Logger log = LoggerFactory.getLogger(TransactionIngestService.class);

    private final TransactionRepository repository;
    private final KafkaTemplate<String, TransactionEvent> kafka;
    private final String topic;
    private final FraudMetrics metrics;

    public TransactionIngestService(
            TransactionRepository repository,
            KafkaTemplate<String, TransactionEvent> kafka,
            @Value("${fraudshield.topics.transactions}") String topic,
            FraudMetrics metrics) {
        this.repository = repository;
        this.kafka = kafka;
        this.topic = topic;
        this.metrics = metrics;
    }

    @Transactional
    public IngestResult ingest(TransactionEvent event) {
        Timer.Sample sample = metrics.startIngest();
        try {
            Optional<TransactionRecord> existing = repository.findById(event.eventId());
            if (existing.isPresent()) {
                metrics.duplicateIgnored();
                return new IngestResult(existing.get(), true);
            }
            TransactionRecord saved = repository.saveAndFlush(new TransactionRecord(event));
            runAfterCommit(() -> {
                metrics.transactionIngested();
                publish(event);
            });
            return new IngestResult(saved, false);
        } finally {
            metrics.stopIngest(sample);
        }
    }

    @Transactional(readOnly = true)
    public Optional<TransactionRecord> find(String eventId) {
        return repository.findById(eventId);
    }

    @Transactional(readOnly = true)
    public List<TransactionRecord> recentForCustomer(String customerId) {
        return repository.findTop100ByCustomerIdOrderByEventTimeDesc(customerId);
    }

    private void publish(TransactionEvent event) {
        try {
            // Keyed by customerId: all events of a customer land in one partition, in order.
            kafka.send(topic, event.customerId(), event).whenComplete((result, error) -> {
                if (error != null) {
                    metrics.publishFailed();
                    log.error("Kafka publish failed for event {}", event.eventId(), error);
                }
            });
        } catch (RuntimeException e) {
            // The row is already committed; never fail the HTTP request because of the broker.
            metrics.publishFailed();
            log.error("Kafka publish could not be started for event {}", event.eventId(), e);
        }
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
