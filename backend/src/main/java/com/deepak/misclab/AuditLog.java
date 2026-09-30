package com.deepak.misclab;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** MongoDB document: schemaless audit trail, one per created receipt. */
@Document("audit_log")
public record AuditLog(@Id String id, String action, String receiptId, String payer, Instant at) {}
