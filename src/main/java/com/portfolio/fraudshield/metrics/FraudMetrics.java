package com.portfolio.fraudshield.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class FraudMetrics {

    private final MeterRegistry registry;
    private final Counter ingested;
    private final Counter duplicates;
    private final Counter publishFailures;
    private final Timer ingestDuration;

    public FraudMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.ingested = Counter.builder("fraudshield.transactions.ingested")
                .description("Transactions committed to the database")
                .register(registry);
        this.duplicates = Counter.builder("fraudshield.transactions.duplicates")
                .description("Submissions ignored because the eventId was already stored")
                .register(registry);
        this.publishFailures = Counter.builder("fraudshield.kafka.publish.failures")
                .description("Committed transactions that could not be published to Kafka")
                .register(registry);
        this.ingestDuration = Timer.builder("fraudshield.ingest.duration")
                .description("Time spent in the ingest service (database write; Kafka publish is asynchronous)")
                .register(registry);
    }

    public void transactionIngested() { ingested.increment(); }

    public void duplicateIgnored() { duplicates.increment(); }

    public void publishFailed() { publishFailures.increment(); }

    public Timer.Sample startIngest() { return Timer.start(registry); }

    public void stopIngest(Timer.Sample sample) { sample.stop(ingestDuration); }
}
