package com.portfolio.fraudshield.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.domain.Persistable;

/**
 * Persistent form of a {@link TransactionEvent}.
 *
 * <p>The id is assigned by the caller, so Spring Data would normally treat every instance as
 * "not new" and call {@code merge()}, which can silently overwrite a row inserted concurrently.
 * Implementing {@link Persistable} makes {@code save()} call {@code persist()} instead, so a
 * concurrent duplicate fails with a primary-key violation rather than an overwrite.
 */
@Entity
@Table(name = "fraud_transactions", indexes = {
        @Index(name = "idx_tx_customer_time", columnList = "customer_id,event_time"),
        @Index(name = "idx_tx_account_time", columnList = "account_id,event_time"),
        @Index(name = "idx_tx_event_time", columnList = "event_time")
})
public class TransactionRecord implements Persistable<String> {

    @Id
    @Column(name = "event_id", length = 80)
    private String eventId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Column(name = "merchant_id", length = 100)
    private String merchantId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    private Double latitude;

    private Double longitude;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "labelled_fraud", nullable = false)
    private boolean labelledFraud;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected TransactionRecord() {
        // for JPA
    }

    public TransactionRecord(TransactionEvent e) {
        this.eventId = e.eventId();
        this.customerId = e.customerId();
        this.accountId = e.accountId();
        this.merchantId = e.merchantId();
        this.amount = e.amount();
        this.currency = e.currency();
        this.status = e.status();
        this.eventTime = e.eventTime();
        this.latitude = e.latitude();
        this.longitude = e.longitude();
        this.countryCode = e.countryCode();
        this.labelledFraud = e.labelledFraud();
        this.createdAt = Instant.now();
    }

    public TransactionEvent toEvent() {
        return new TransactionEvent(eventId, customerId, accountId, merchantId, amount, currency,
                status, eventTime, latitude, longitude, countryCode, labelledFraud);
    }

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public String getEventId() { return eventId; }
    public String getCustomerId() { return customerId; }
    public String getAccountId() { return accountId; }
    public String getMerchantId() { return merchantId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public TransactionStatus getStatus() { return status; }
    public Instant getEventTime() { return eventTime; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getCountryCode() { return countryCode; }
    public boolean isLabelledFraud() { return labelledFraud; }
    public Instant getCreatedAt() { return createdAt; }
}
