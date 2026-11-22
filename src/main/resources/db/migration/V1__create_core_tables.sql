-- Week 1: core transaction store.
-- Every transaction accepted by the ingest API is persisted here and then published to Kafka.
CREATE TABLE fraud_transactions (
    event_id       VARCHAR(80)      PRIMARY KEY,
    customer_id    VARCHAR(100)     NOT NULL,
    account_id     VARCHAR(100)     NOT NULL,
    merchant_id    VARCHAR(100),
    amount         NUMERIC(19, 4)   NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)       NOT NULL,
    status         VARCHAR(20)      NOT NULL,
    event_time     TIMESTAMPTZ      NOT NULL,
    latitude       DOUBLE PRECISION,
    longitude      DOUBLE PRECISION,
    country_code   VARCHAR(2),
    labelled_fraud BOOLEAN          NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ      NOT NULL
);

CREATE INDEX idx_tx_customer_time ON fraud_transactions (customer_id, event_time);
CREATE INDEX idx_tx_account_time  ON fraud_transactions (account_id, event_time);
CREATE INDEX idx_tx_event_time    ON fraud_transactions (event_time);
