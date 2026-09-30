package com.deepak.misclab.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * The payments schema and repository on Oracle Database 23ai Free (gvenzl/oracle-free, arm64) instead of PostgreSQL.
 * Profile "oracle" points Flyway at db/oracle; Hibernate validates the same @Entity against the Oracle tables.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("oracle")
@Testcontainers
class OraclePaymentRepositoryIT {
    @Container @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(java.time.Duration.ofMinutes(5));   // shared, busy Docker VM: allow a slow first start

    @Autowired PaymentRepository repo;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    static final Instant AT = Instant.parse("2026-09-30T10:15:30.123456Z");

    static PaymentIntent intent(String id, String receipt) {
        return new PaymentIntent(id, receipt, new Money(1500, "INR"), new Money(0, "INR"), new Money(0, "INR"),
                PaymentStatus.CREATED, AT);
    }

    @Test void flywayRanTheOracleMigration() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = 1",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_tables WHERE table_name IN "
                + "('PAYMENT_INTENTS','IDEMPOTENCY_KEYS','WEBHOOK_OUTBOX','WEBHOOK_RECEIVED_EVENTS')", Integer.class)).isEqualTo(4);
    }

    @Test void saveReadBackLockAndVersion() {
        repo.saveAndFlush(PaymentEntity.from(intent("pi_ora_1", "rc_1")));
        em.clear();

        PaymentEntity locked = repo.findByIdForUpdate("pi_ora_1").orElseThrow();      // SELECT ... FOR UPDATE on Oracle
        assertThat(locked.toDomain().amount()).isEqualTo(new Money(1500, "INR"));
        assertThat(locked.toDomain().updatedAt()).isEqualTo(AT);                     // microseconds survive TIMESTAMP(6)
        assertThat(locked.version()).isZero();

        locked.apply(locked.toDomain().authorize(AT.plusSeconds(1)));
        repo.saveAndFlush(locked);                                                   // UPDATE ... WHERE version = 0
        em.clear();
        PaymentEntity reread = repo.findById("pi_ora_1").orElseThrow();
        assertThat(reread.toDomain().status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(reread.version()).isEqualTo(1L);
        assertThat(repo.countByReceiptId("rc_1")).isEqualTo(1);
    }

    @Test void checkConstraintsHoldOnOracleToo() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment_intents (id, receipt_id, currency, amount_minor, captured_minor, "
                + "refunded_minor, status, updated_at) VALUES ('pi_bad', 'rc', 'INR', 100, 50, 60, 'CAPTURED', SYSTIMESTAMP)"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("REFUND_LE_CAPTURE");
    }

    @Test void outboxIdentityColumnAndClobBody() {
        repo.saveAndFlush(PaymentEntity.from(intent("pi_ora_2", "rc_2")));
        String bigBody = "{\"pad\":\"" + "x".repeat(10_000) + "\"}";                  // > 4000 bytes: needs CLOB, not VARCHAR2
        jdbc.update("INSERT INTO webhook_outbox (event_id, type, payment_id, body, signature) VALUES (?, ?, ?, ?, ?)",
                "evt_1", "payment.created", "pi_ora_2", bigBody, "t=1,v1=abc");
        jdbc.update("INSERT INTO webhook_outbox (event_id, type, payment_id, body, signature) VALUES (?, ?, ?, ?, ?)",
                "evt_2", "payment.authorized", "pi_ora_2", "{}", "t=2,v1=def");
        assertThat(jdbc.queryForList("SELECT event_id FROM webhook_outbox WHERE payment_id = ? ORDER BY seq", String.class, "pi_ora_2"))
                .containsExactly("evt_1", "evt_2");
        assertThat(jdbc.queryForObject("SELECT DBMS_LOB.GETLENGTH(body) FROM webhook_outbox WHERE event_id = 'evt_1'", Integer.class))
                .isEqualTo(bigBody.length());
    }
}
