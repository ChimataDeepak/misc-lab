-- Query-first design: the table exists to answer ONE query, "latest N receipts of a payer".
-- Partition key payer   -> all of a payer's receipts live together on the same replicas (one-partition read).
-- Clustering created_at DESC -> rows stored newest first, so "LIMIT n" is a sequential read, no sort.
-- receipt_id as last clustering column -> two receipts at the same millisecond don't overwrite each other.
-- Lab replication (1 node). Production: NetworkTopologyStrategy with RF 3 per DC. A payer with millions of
-- receipts would need a time bucket in the partition key, e.g. PRIMARY KEY ((payer, month), created_at, receipt_id).
CREATE KEYSPACE IF NOT EXISTS misclab WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS misclab.receipts_by_payer (
    payer       text,
    created_at  timestamp,
    receipt_id  text,
    amount      decimal,
    currency    text,
    usd_amount  decimal,
    PRIMARY KEY ((payer), created_at, receipt_id)
) WITH CLUSTERING ORDER BY (created_at DESC, receipt_id ASC);
