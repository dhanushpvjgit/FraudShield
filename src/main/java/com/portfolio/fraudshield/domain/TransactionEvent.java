package com.portfolio.fraudshield.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable transaction event: the message published to Kafka and consumed by the stream processor.
 *
 * @param labelledFraud ground-truth label set only by the simulator; used to measure detection rate
 *                      and false-positive rate. Real traffic always carries {@code false}.
 */
public record TransactionEvent(
        String eventId,
        String customerId,
        String accountId,
        String merchantId,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        Instant eventTime,
        Double latitude,
        Double longitude,
        String countryCode,
        boolean labelledFraud) {

    /** Convenience constructor for unlabelled (real) traffic. */
    public TransactionEvent(
            String eventId,
            String customerId,
            String accountId,
            String merchantId,
            BigDecimal amount,
            String currency,
            TransactionStatus status,
            Instant eventTime,
            Double latitude,
            Double longitude,
            String countryCode) {
        this(eventId, customerId, accountId, merchantId, amount, currency, status,
                eventTime, latitude, longitude, countryCode, false);
    }
}
