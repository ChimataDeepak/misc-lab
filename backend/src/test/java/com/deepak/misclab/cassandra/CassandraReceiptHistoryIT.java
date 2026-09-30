package com.deepak.misclab.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import com.deepak.misclab.Receipt;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real Cassandra 4.1 (Testcontainers). Schema comes from src/main/resources/cassandra/schema.cql. */
@SpringBootTest(properties = {"misclab.aws.init-resources=false", "misclab.poller.enabled=false",
        "misclab.cassandra.enabled=true", "spring.autoconfigure.exclude=", "spring.cassandra.keyspace-name=misclab"})
@Testcontainers
class CassandraReceiptHistoryIT {
    @Container @ServiceConnection
    static CassandraContainer cassandra = new CassandraContainer("cassandra:4.1");

    @Autowired CassandraReceiptHistory history;
    @Autowired CqlSession session;

    @BeforeAll
    static void createSchema() throws Exception {
        String cql = new ClassPathResource("cassandra/schema.cql").getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("(?m)^--.*$", "");
        try (CqlSession s = CqlSession.builder().addContactPoint(cassandra.getContactPoint())
                .withLocalDatacenter(cassandra.getLocalDatacenter())
                .withConfigLoader(DriverConfigLoader.programmaticBuilder()
                        .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(30)).build())
                .build()) {
            for (String stmt : cql.split(";")) if (!stmt.isBlank()) s.execute(stmt.trim());
        }
    }

    static Receipt receipt(String payer, Instant at, String usd) {
        return new Receipt(UUID.randomUUID().toString(), payer, new BigDecimal(usd), "USD", new BigDecimal(usd), at);
    }

    @Test void latestNForPayerComesBackNewestFirstFromOnePartition() {
        String payer = "payer-" + UUID.randomUUID();
        Instant t0 = Instant.parse("2026-09-01T00:00:00Z");
        for (int i = 0; i < 5; i++) history.onReceiptCreated(receipt(payer, t0.plusSeconds(60L * i), "1" + i), "{}"); // inserted oldest first
        history.onReceiptCreated(receipt("someone-else", t0.plusSeconds(3600), "99"), "{}");

        List<ReceiptByPayer> latest = history.latest(payer, 3);

        assertThat(latest).extracting(r -> r.key().createdAt())
                .containsExactly(t0.plusSeconds(240), t0.plusSeconds(180), t0.plusSeconds(120));
        assertThat(latest).allSatisfy(r -> assertThat(r.key().payer()).isEqualTo(payer));
        assertThat(latest.get(0).usdAmount()).isEqualByComparingTo("14");
    }

    @Test void sameMillisecondReceiptsDoNotOverwriteAndRewriteIsAnUpsert() {
        String payer = "payer-" + UUID.randomUUID();
        Instant at = Instant.parse("2026-09-02T00:00:00Z");
        Receipt a = receipt(payer, at, "5"), b = receipt(payer, at, "6");
        history.onReceiptCreated(a, "{}");
        history.onReceiptCreated(b, "{}");
        history.onReceiptCreated(a, "{}");                    // redelivered event: same key, same row
        assertThat(history.latest(payer, 10)).hasSize(2);
    }

    @Test void tableIsClusteredByCreatedAtDescending() {
        var row = session.execute("SELECT clustering_order FROM system_schema.columns WHERE keyspace_name='misclab' "
                + "AND table_name='receipts_by_payer' AND column_name='created_at'").one();
        assertThat(row.getString("clustering_order")).isEqualTo("desc");
    }
}
