package com.portfolio.fraudshield.simulator;

/** Summary of one simulator run. {@code runId} is embedded in every generated customer id. */
public record SimulationResult(
        String runId,
        int generated,
        int legitimateEvents,
        int labelledFraudEvents,
        int labelledFraudClusters) {
}
