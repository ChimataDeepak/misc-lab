-- Read-path indexes for GET /api/statements/{payerId} (measured in PERF-REPORT.md, EXPLAIN ANALYZE before/after).
-- 1) "latest N receipts of a payer": equality on payer_id, then ordered by created_at DESC -> one composite index
--    serves both the filter and the ORDER BY, so Postgres reads exactly N index entries (no sort, no full scan).
--    The trailing id DESC matches the query's tie-break (ORDER BY created_at DESC, id DESC); without it EXPLAIN shows
--    an extra Incremental Sort node (measured: PERF-REPORT.md, step 3).
CREATE INDEX ledger_receipts_payer_created_idx ON ledger_receipts (payer_id, created_at DESC, id DESC);
-- 2) lines of a receipt: the foreign key column. Without it every lookup is a sequential scan of all lines.
CREATE INDEX ledger_receipt_lines_receipt_idx ON ledger_receipt_lines (receipt_id);
