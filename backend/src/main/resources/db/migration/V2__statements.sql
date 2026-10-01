-- Payer statements: receipts in a relational ledger, each with line items.
-- Deliberately created WITHOUT the read-path indexes; V3 adds them. The perf harness (StatementPerfBench)
-- drops/re-creates the V3 indexes to measure the "before" and "after" (see PERF-REPORT.md).
CREATE TABLE ledger_receipts (
    id           BIGSERIAL    PRIMARY KEY,
    payer_id     BIGINT       NOT NULL,
    merchant     VARCHAR(100) NOT NULL,
    amount_minor BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency     VARCHAR(3)   NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE ledger_receipt_lines (
    id           BIGSERIAL    PRIMARY KEY,
    receipt_id   BIGINT       NOT NULL REFERENCES ledger_receipts (id),   -- Postgres does NOT index FK columns for you
    description  VARCHAR(200) NOT NULL,
    amount_minor BIGINT       NOT NULL
);
