package com.portfolio.fraudshield.simulator;

import com.portfolio.fraudshield.domain.TransactionEvent;
import com.portfolio.fraudshield.domain.TransactionStatus;
import com.portfolio.fraudshield.service.TransactionIngestService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Generates labelled synthetic traffic and pushes it through the normal ingest path.
 *
 * <h2>Legitimate traffic</h2>
 * Spread over a pool of customers sized so that no customer gets more than two events per run,
 * each tied to a fixed "home" city. Amounts are $5 to $500, about 2% are declined. By construction
 * legitimate customers cannot trip the velocity (5+ events), repeated-failure (3+ declines),
 * amount-spike (>= $5,000) or impossible-travel rules, so every alert on a legitimate customer is
 * a measurable false positive.
 *
 * <h2>Labelled fraud clusters</h2>
 * One cluster (5 events, one customer) per 100 requested events, minimum one when count >= 10:
 * <ol>
 *   <li>three small DECLINED attempts (repeated failures)</li>
 *   <li>one normal APPROVED purchase at home</li>
 *   <li>one $7,500 APPROVED purchase in Sydney seconds later (amount spike + impossible travel)</li>
 * </ol>
 * Five events inside seconds also satisfy the velocity rule. Every cluster event carries
 * {@code labelledFraud = true}.
 *
 * <p>Customer ids contain a per-run id, so repeated runs never share customers and therefore never
 * interfere with each other's windows.
 */
@Service
public class TransactionSimulator {

    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 10_000;

    static final int CLUSTER_SIZE = 5;
    static final int EVENTS_PER_CLUSTER = 100;
    static final int MIN_COUNT_FOR_FRAUD = 10;

    private static final double[][] HOME_COORDS = {
            {37.77, -122.42},   // San Francisco
            {40.71, -74.01},    // New York
            {51.51, -0.13},     // London
            {48.14, 11.58},     // Munich
            {35.68, 139.69}     // Tokyo
    };
    private static final String[] HOME_COUNTRIES = {"US", "US", "GB", "DE", "JP"};
    private static final double REMOTE_LAT = -33.86;   // Sydney
    private static final double REMOTE_LON = 151.20;

    private final TransactionIngestService ingest;
    private final Clock clock;
    private final Random random;

    public TransactionSimulator(
            TransactionIngestService ingest,
            Clock clock,
            @Value("${fraudshield.simulator.seed:42}") long seed) {
        this.ingest = ingest;
        this.clock = clock;
        this.random = new Random(seed);
    }

    public SimulationResult generate(int count) {
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new IllegalArgumentException(
                    "count must be between " + MIN_COUNT + " and " + MAX_COUNT);
        }
        Instant now = clock.instant();
        String runId = Long.toString(now.toEpochMilli(), 36);

        int clusters = count >= MIN_COUNT_FOR_FRAUD ? Math.max(1, count / EVENTS_PER_CLUSTER) : 0;
        int fraudEvents = clusters * CLUSTER_SIZE;
        int legitimateEvents = count - fraudEvents;
        // Pool >= legitimateEvents / 2 so each customer gets at most 2 events per run.
        int customerPool = Math.max(500, (legitimateEvents + 1) / 2);

        int nextCluster = 0;
        for (int n = 0; n < legitimateEvents; n++) {
            // Interleave clusters evenly between legitimate events.
            while (nextCluster < clusters
                    && clusterPosition(nextCluster, clusters, legitimateEvents) <= n) {
                emitCluster(runId, nextCluster++, now);
            }
            ingest.ingest(legitimateEvent(runId, n % customerPool, now));
        }
        while (nextCluster < clusters) {
            emitCluster(runId, nextCluster++, now);
        }
        return new SimulationResult(runId, count, legitimateEvents, fraudEvents, clusters);
    }

    private static int clusterPosition(int cluster, int clusters, int legitimateEvents) {
        return (int) ((cluster + 1L) * legitimateEvents / (clusters + 1));
    }

    private TransactionEvent legitimateEvent(String runId, int customerSlot, Instant now) {
        int home = customerSlot % HOME_COORDS.length;
        String customer = "customer-" + runId + "-" + customerSlot;
        TransactionStatus status = random.nextInt(100) < 2
                ? TransactionStatus.DECLINED : TransactionStatus.APPROVED;
        return new TransactionEvent(
                eventId(), customer, "acct-" + customer, merchant(),
                BigDecimal.valueOf(500 + random.nextInt(49_501), 2),   // 5.00 .. 500.00
                "USD", status,
                now.minusSeconds(random.nextInt(60)),
                HOME_COORDS[home][0] + (random.nextDouble() - 0.5) * 0.5,
                HOME_COORDS[home][1] + (random.nextDouble() - 0.5) * 0.5,
                HOME_COUNTRIES[home],
                false);
    }

    private void emitCluster(String runId, int cluster, Instant now) {
        int home = cluster % HOME_COORDS.length;
        String customer = "labelled-fraud-cluster-" + runId + "-" + cluster;
        String account = "acct-" + customer;
        for (int slot = 0; slot < CLUSTER_SIZE; slot++) {
            boolean declined = slot < 3;
            boolean remote = slot == 4;
            BigDecimal amount;
            if (remote) {
                amount = new BigDecimal("7500.00");
            } else if (declined) {
                amount = BigDecimal.valueOf(100 + random.nextInt(1_901), 2);   // 1.00 .. 20.00
            } else {
                amount = BigDecimal.valueOf(500 + random.nextInt(49_501), 2);
            }
            ingest.ingest(new TransactionEvent(
                    eventId(), customer, account, merchant(), amount, "USD",
                    declined ? TransactionStatus.DECLINED : TransactionStatus.APPROVED,
                    now.minusSeconds(10L - 2L * slot),   // strictly increasing: -10s .. -2s
                    remote ? REMOTE_LAT : HOME_COORDS[home][0],
                    remote ? REMOTE_LON : HOME_COORDS[home][1],
                    remote ? "AU" : HOME_COUNTRIES[home],
                    true));
        }
    }

    private String merchant() {
        return "merchant-" + random.nextInt(50);
    }

    private static String eventId() {
        return "sim-" + UUID.randomUUID();
    }
}
