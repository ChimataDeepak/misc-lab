package com.deepak.misclab;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    List<AuditLog> findByReceiptId(String receiptId);
}
