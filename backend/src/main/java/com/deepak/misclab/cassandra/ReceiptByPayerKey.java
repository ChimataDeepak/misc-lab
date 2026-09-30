package com.deepak.misclab.cassandra;

import java.time.Instant;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

/** Composite primary key: (payer) partition, then created_at DESC, receipt_id clustering. */
@PrimaryKeyClass
public record ReceiptByPayerKey(
        @PrimaryKeyColumn(name = "payer", ordinal = 0, type = PrimaryKeyType.PARTITIONED) String payer,
        @PrimaryKeyColumn(name = "created_at", ordinal = 1, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING) Instant createdAt,
        @PrimaryKeyColumn(name = "receipt_id", ordinal = 2, type = PrimaryKeyType.CLUSTERED) String receiptId) {}
