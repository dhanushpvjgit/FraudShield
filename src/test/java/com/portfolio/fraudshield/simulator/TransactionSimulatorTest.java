package com.portfolio.fraudshield.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portfolio.fraudshield.domain.TransactionEvent;
import com.portfolio.fraudshield.domain.TransactionRecord;
import com.portfolio.fraudshield.domain.TransactionStatus;
import com.portfolio.fraudshield.service.IngestResult;
import com.portfolio.fraudshield.service.TransactionIngestService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransactionSimulatorTest {

    private static final BigDecimal AMOUNT_THRESHOLD = new BigDecimal("5000");

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private TransactionIngestService ingest;
    private TransactionSimulator simulator;

    @BeforeEach
    void setUp() {
        ingest = mock(TransactionIngestService.class);
        when(ingest.ingest(any())).thenAnswer(inv ->
                new IngestResult(new TransactionRecord(inv.getArgument(0)), false));
        simulator = new TransactionSimulator(ingest, clock, 42L);
    }

    private List<TransactionEvent> run(int count) {
        simulator.generate(count);
        ArgumentCaptor<TransactionEvent> captor = ArgumentCaptor.forClass(TransactionEvent.class);
        verify(ingest, org.mockito.Mockito.times(count)).ingest(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void generatesExactlyTheRequestedNumberOfUniqueEvents() {
        List<TransactionEvent> events = run(1000);

        assertThat(events).hasSize(1000);
        assertThat(events.stream().map(TransactionEvent::eventId).distinct()).hasSize(1000);
    }

    @Test
    void createsOneLabelledFraudClusterPerHundredEvents() {
        SimulationResult result = simulator.generate(1000);

        assertThat(result.labelledFraudClusters()).isEqualTo(10);
        assertThat(result.labelledFraudEvents()).isEqualTo(50);
        assertThat(result.legitimateEvents()).isEqualTo(950);
        assertThat(result.generated()).isEqualTo(1000);
    }

    @Test
    void tinyRunsHaveNoFraudAndSmallRunsStillGetOneCluster() {
        assertThat(simulator.generate(9).labelledFraudClusters()).isZero();
        assertThat(simulator.generate(10).labelledFraudClusters()).isEqualTo(1);
    }

    @Test
    void everyFraudClusterTripsAllFourPatterns() {
        List<TransactionEvent> events = run(1000);
        Map<String, List<TransactionEvent>> clusters = events.stream()
                .filter(TransactionEvent::labelledFraud)
                .collect(Collectors.groupingBy(TransactionEvent::customerId));

        assertThat(clusters).hasSize(10);
        clusters.values().forEach(cluster -> {
            assertThat(cluster).hasSize(5);                                            // velocity
            assertThat(cluster.stream()
                    .filter(e -> e.status() == TransactionStatus.DECLINED)).hasSize(3);  // repeated failures
            assertThat(cluster.stream()
                    .anyMatch(e -> e.amount().compareTo(AMOUNT_THRESHOLD) >= 0)).isTrue(); // amount spike
            assertThat(cluster.stream().map(TransactionEvent::countryCode).distinct())
                    .hasSizeGreaterThan(1);                                             // impossible travel
            assertThat(Duration.between(cluster.get(0).eventTime(),
                    cluster.get(cluster.size() - 1).eventTime())).isLessThan(Duration.ofMinutes(1));
        });
    }

    @Test
    void legitimateCustomersCanNeverTripAnyRule() {
        List<TransactionEvent> events = run(10_000);
        Map<String, List<TransactionEvent>> legit = events.stream()
                .filter(e -> !e.labelledFraud())
                .collect(Collectors.groupingBy(TransactionEvent::customerId));

        legit.values().forEach(txs -> {
            assertThat(txs.size()).isLessThan(5);                                       // velocity
            assertThat(txs.stream()
                    .filter(e -> e.status() == TransactionStatus.DECLINED).count()).isLessThan(3); // failures
            assertThat(txs.stream()
                    .allMatch(e -> e.amount().compareTo(AMOUNT_THRESHOLD) < 0)).isTrue(); // amount spike
            assertThat(txs.stream().map(TransactionEvent::countryCode).distinct()).hasSize(1); // travel
        });
    }

    @Test
    void usesCustomerIdAsPartitioningIdentityAndSetsAccount() {
        run(200).forEach(e -> {
            assertThat(e.customerId()).isNotBlank();
            assertThat(e.accountId()).isEqualTo("acct-" + e.customerId());
            assertThat(e.amount()).isGreaterThan(BigDecimal.ZERO);
        });
    }

    @Test
    void rejectsCountsOutsideTheSupportedRange() {
        assertThatThrownBy(() -> simulator.generate(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> simulator.generate(-5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> simulator.generate(10_001)).isInstanceOf(IllegalArgumentException.class);
    }
}
