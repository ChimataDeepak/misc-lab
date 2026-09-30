package com.deepak.misclab.cassandra;

import java.util.List;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

public interface ReceiptByPayerRepository extends CassandraRepository<ReceiptByPayer, ReceiptByPayerKey> {

    /** Single-partition query; rows come back newest first because of the clustering order, so no ORDER BY needed. */
    @Query("SELECT * FROM receipts_by_payer WHERE payer = ?0 LIMIT ?1")
    List<ReceiptByPayer> latestForPayer(String payer, int limit);
}
