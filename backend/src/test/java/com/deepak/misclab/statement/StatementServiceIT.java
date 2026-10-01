package com.deepak.misclab.statement;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Correctness guard for the tuned read path: the 2-query fetch-join version must return exactly what the naive
 * N+1 version returns, and the SQL statement counts must be what PERF-REPORT.md claims (1+N vs 2).
 */
@SpringBootTest(properties = {"misclab.aws.init-resources=false", "misclab.poller.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
class StatementServiceIT {

    @Autowired LedgerReceiptRepository repo;
    @Autowired EntityManagerFactory emf;
    @Autowired org.springframework.transaction.PlatformTransactionManager txm;
    Statistics stats;
    StatementService naive, join, cached;
    static final long PAYER = 900_001;

    @BeforeEach
    void setUp() {
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        naive = new StatementService(repo, "naive", Duration.ZERO);
        join = new StatementService(repo, "join", Duration.ZERO);
        cached = new StatementService(repo, "join", Duration.ofMinutes(1));
        if (repo.findLatestIds(PAYER, org.springframework.data.domain.PageRequest.of(0, 1)).isEmpty()) {
            for (int i = 0; i < 12; i++) {
                join.addReceipt(PAYER, "shop-" + i, i % 2 == 0 ? "INR" : "USD",
                        List.of(new Statement.Line("a", 100 + i), new Statement.Line("b", 200), new Statement.Line("c", 5)));
            }
        }
    }

    /** These services are built with `new` (no Spring proxy), so wrap each call in its own read-only transaction, like a request. */
    private Statement read(StatementService s, int limit) {
        var tx = new org.springframework.transaction.support.TransactionTemplate(txm);
        tx.setReadOnly(true);
        return tx.execute(st -> s.statement(PAYER, limit));
    }

    private long statementsFor(StatementService s, int limit) {
        stats.clear();
        read(s, limit);
        return stats.getPrepareStatementCount();
    }

    @Test
    void joinFetchReturnsSameStatementAsNaive() {
        Statement a = read(naive, 10);
        Statement b = read(join, 10);
        assertThat(b).isEqualTo(a);
        assertThat(b.receiptCount()).isEqualTo(10);
        assertThat(b.receipts().get(0).createdAt()).isAfterOrEqualTo(b.receipts().get(9).createdAt());   // newest first
        assertThat(b.receipts().get(0).lines()).extracting(Statement.Line::description).containsExactly("a", "b", "c");
        long sum = b.receipts().stream().mapToLong(Statement.Item::amountMinor).sum();
        assertThat(b.totalMinorByCurrency().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(sum);
    }

    @Test
    void naiveIsNPlusOneJoinIsTwoQueries() {
        assertThat(statementsFor(naive, 10)).isEqualTo(1 + 10);   // 1 page query + 1 lines query per receipt
        assertThat(statementsFor(join, 10)).isEqualTo(2);         // ids page + one fetch join
    }

    @Test
    void cacheServesRepeatsAndIsEvictedByWrite() {
        Statement first = read(cached, 5);
        assertThat(statementsFor(cached, 5)).isZero();                                    // second read: no SQL at all
        long id = cached.addReceipt(PAYER, "new-shop", "INR", List.of(new Statement.Line("x", 1)));
        Statement after = read(cached, 5);
        assertThat(after.receipts().get(0).id()).isEqualTo(id);                         // write evicted the stale entry
        assertThat(after).isNotEqualTo(first);
    }
}
