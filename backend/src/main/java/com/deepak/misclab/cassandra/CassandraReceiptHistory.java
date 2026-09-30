package com.deepak.misclab.cassandra;

import com.deepak.misclab.Receipt;
import com.deepak.misclab.ReceiptEventSink;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;
import org.springframework.stereotype.Service;

/**
 * Receipt history per payer on Cassandra: written as an extra sink after each receipt is stored, read as
 * "latest N for payer". Writes are idempotent upserts (same primary key = same row), which suits at-least-once feeds.
 */
@Service
@ConditionalOnProperty(name = "misclab.cassandra.enabled", havingValue = "true")
public class CassandraReceiptHistory implements ReceiptEventSink {
    private final ReceiptByPayerRepository repo;

    public CassandraReceiptHistory(ReceiptByPayerRepository repo) { this.repo = repo; }

    @Override
    public void onReceiptCreated(Receipt receipt, String receiptJson) { repo.save(ReceiptByPayer.from(receipt)); }

    public List<ReceiptByPayer> latest(String payer, int n) {
        if (n < 1 || n > 1000) throw new IllegalArgumentException("n must be 1..1000");
        return repo.latestForPayer(payer, n);
    }

    @Configuration
    @ConditionalOnProperty(name = "misclab.cassandra.enabled", havingValue = "true")
    @EnableCassandraRepositories(basePackageClasses = ReceiptByPayerRepository.class)
    static class Repos { }
}
