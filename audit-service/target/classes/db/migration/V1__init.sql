-- Idempotency guard: one row per trade successfully processed. The Kafka
-- record key published by Debezium's outbox EventRouter is the trade's own
-- id (see order-service's register-postgres-connector.json,
-- transforms.outbox.table.field.event.key = aggregate_id), and each trade
-- produces exactly one outbox event, so the trade id alone is a sufficient
-- idempotency key here.
CREATE TABLE inbox_events (
    id              UUID PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The audit service's own record of every trade it evaluated, and what it
-- decided. Deliberately holds enough of the trade's own fields to run rules
-- like the velocity check against audit-service's own history, instead of
-- querying order-service's trades table directly across services.
CREATE TABLE audit_results (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_id            UUID            NOT NULL,
    account_id          VARCHAR(64)     NOT NULL,
    counterparty        VARCHAR(128)    NOT NULL,
    country_code        VARCHAR(2)      NOT NULL,
    notional_amount     NUMERIC(18, 2)  NOT NULL,
    currency            VARCHAR(3)      NOT NULL,
    flagged             BOOLEAN         NOT NULL,
    reasons             TEXT,
    processed_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_results_account_id_processed_at
    ON audit_results (account_id, processed_at);
