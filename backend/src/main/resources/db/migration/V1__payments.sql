-- Payment intents. Money is integer minor units (paise/cents) + ISO-4217 code, never floating point.
-- version: optimistic-lock counter maintained by JPA (@Version); transitions additionally take a row lock (SELECT ... FOR UPDATE).
CREATE TABLE payment_intents (
    id              VARCHAR(64)  PRIMARY KEY,
    receipt_id      VARCHAR(128) NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    amount_minor    BIGINT       NOT NULL CHECK (amount_minor > 0),
    captured_minor  BIGINT       NOT NULL DEFAULT 0 CHECK (captured_minor >= 0),
    refunded_minor  BIGINT       NOT NULL DEFAULT 0 CHECK (refunded_minor >= 0),
    status          VARCHAR(32)  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT refund_le_capture CHECK (refunded_minor <= captured_minor),
    CONSTRAINT capture_le_amount CHECK (captured_minor <= amount_minor)
);
CREATE INDEX payment_intents_receipt_idx ON payment_intents (receipt_id);

-- Idempotency-Key store. The primary key IS the guarantee: two concurrent requests with the same (scope, key)
-- cannot both insert; the loser waits for the winner's transaction and then reads the stored response.
CREATE TABLE idempotency_keys (
    scope          VARCHAR(128) NOT NULL,          -- "create", "capture:<pi id>", "refund:<pi id>"
    idem_key       VARCHAR(255) NOT NULL,
    fingerprint    VARCHAR(512) NOT NULL,          -- request body identity; same key + different body -> 422
    response_json  TEXT,                           -- stored result, returned verbatim to retries
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (scope, idem_key)
);

-- Outbound webhook events (outbox). Written in the same transaction as the state change they describe.
CREATE TABLE webhook_outbox (
    seq        BIGSERIAL    PRIMARY KEY,           -- insertion order
    event_id   VARCHAR(64)  NOT NULL UNIQUE,
    type       VARCHAR(64)  NOT NULL,
    payment_id VARCHAR(64)  NOT NULL REFERENCES payment_intents (id),
    body       TEXT         NOT NULL,
    signature  VARCHAR(256) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX webhook_outbox_payment_idx ON webhook_outbox (payment_id);

-- Receiver side: event ids already accepted, for replay protection that survives a restart.
CREATE TABLE webhook_received_events (
    event_id    VARCHAR(64) PRIMARY KEY,
    signed_at   BIGINT      NOT NULL,               -- the t= from the signature header (unix seconds)
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX webhook_received_signed_idx ON webhook_received_events (signed_at);
