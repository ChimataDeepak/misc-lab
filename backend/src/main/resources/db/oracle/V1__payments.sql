-- Oracle dialect of db/migration/V1__payments.sql (PostgreSQL). Same tables, columns, keys and CHECK rules.
-- Differences handled (see README "Oracle portability"):
--   BIGINT -> NUMBER(19); VARCHAR -> VARCHAR2; TEXT -> CLOB; TIMESTAMPTZ -> TIMESTAMP WITH TIME ZONE;
--   now() -> SYSTIMESTAMP; BIGSERIAL -> NUMBER GENERATED ALWAYS AS IDENTITY;
--   Oracle wants DEFAULT before NOT NULL; CHECK constraints are named so violations are recognisable.
CREATE TABLE payment_intents (
    id              VARCHAR2(64)  PRIMARY KEY,
    receipt_id      VARCHAR2(128) NOT NULL,
    currency        VARCHAR2(3)   NOT NULL,
    amount_minor    NUMBER(19)    NOT NULL,
    captured_minor  NUMBER(19)    DEFAULT 0 NOT NULL,
    refunded_minor  NUMBER(19)    DEFAULT 0 NOT NULL,
    status          VARCHAR2(32)  NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version         NUMBER(19)    DEFAULT 0 NOT NULL,
    CONSTRAINT amount_positive   CHECK (amount_minor > 0),
    CONSTRAINT captured_nonneg   CHECK (captured_minor >= 0),
    CONSTRAINT refunded_nonneg   CHECK (refunded_minor >= 0),
    CONSTRAINT refund_le_capture CHECK (refunded_minor <= captured_minor),
    CONSTRAINT capture_le_amount CHECK (captured_minor <= amount_minor)
);
CREATE INDEX payment_intents_receipt_idx ON payment_intents (receipt_id);

CREATE TABLE idempotency_keys (
    scope          VARCHAR2(128) NOT NULL,
    idem_key       VARCHAR2(255) NOT NULL,
    fingerprint    VARCHAR2(512) NOT NULL,
    response_json  CLOB,
    created_at     TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT idempotency_keys_pk PRIMARY KEY (scope, idem_key)
);

CREATE TABLE webhook_outbox (
    seq        NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id   VARCHAR2(64)  NOT NULL UNIQUE,
    type       VARCHAR2(64)  NOT NULL,
    payment_id VARCHAR2(64)  NOT NULL REFERENCES payment_intents (id),
    body       CLOB          NOT NULL,
    signature  VARCHAR2(256) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);
CREATE INDEX webhook_outbox_payment_idx ON webhook_outbox (payment_id);

CREATE TABLE webhook_received_events (
    event_id    VARCHAR2(64) PRIMARY KEY,
    signed_at   NUMBER(19)   NOT NULL,
    received_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);
CREATE INDEX webhook_received_signed_idx ON webhook_received_events (signed_at);
