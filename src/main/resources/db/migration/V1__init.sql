CREATE TABLE payments (
    id          UUID PRIMARY KEY,
    account_id  UUID NOT NULL,
    amount      BIGINT NOT NULL,
    currency    VARCHAR(3) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_keys (
    key           VARCHAR(255) PRIMARY KEY,
    request_hash  VARCHAR(64) NOT NULL,
    response_body JSONB,
    status_code   INT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox (
    id           UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type   VARCHAR(50) NOT NULL,
    payload      JSONB NOT NULL,
    published    BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published = false;
CREATE INDEX idx_payments_account   ON payments (account_id);
