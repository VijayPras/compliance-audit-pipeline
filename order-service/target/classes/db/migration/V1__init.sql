-- Core trades table: what the business actually cares about.
CREATE TABLE trades (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      VARCHAR(64)     NOT NULL,
    counterparty    VARCHAR(128)    NOT NULL,
    country_code    VARCHAR(2)      NOT NULL,
    trade_type      VARCHAR(16)     NOT NULL,
    notional_amount NUMERIC(18, 2)  NOT NULL,
    currency        VARCHAR(3)      NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'EXECUTED',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_trades_account_id ON trades (account_id);
CREATE INDEX idx_trades_created_at ON trades (created_at);

-- Outbox table: written in the SAME transaction as the trades insert.
-- Debezium tails this table's changes off the Postgres WAL and streams
-- them into Kafka -- the application never talks to Kafka directly.
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64)     NOT NULL,
    aggregate_id    VARCHAR(64)     NOT NULL,
    event_type      VARCHAR(64)     NOT NULL,
    payload         JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_events_created_at ON outbox_events (created_at);
