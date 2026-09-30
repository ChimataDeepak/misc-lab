package com.deepak.misclab.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Service against a real PostgreSQL 16 (Testcontainers, see src/test/resources/application.properties).
 * Keys and receipt ids are random per test because the DB is shared across tests in the JVM.
 */
@SpringBootTest(properties = {"misclab.aws.init-resources=false", "misclab.poller.enabled=false"})
class PaymentServiceTest {
    @Autowired PaymentService svc;
    @Autowired WebhookPublisher webhooks;
    @Autowired PaymentRepository repo;
    @Autowired JdbcTemplate jdbc;

    static String uniq(String p) { return p + "-" + UUID.randomUUID(); }

    @Test void sameKeySameBodyReturnsSamePaymentAndNoSecondWebhook() {
        String key = uniq("k"), rid = uniq("r");
        PaymentIntent a = svc.create(rid, new Money(500, "USD"), key);
        PaymentIntent b = svc.create(rid, new Money(500, "USD"), key);
        assertThat(b).isEqualTo(a);                                   // stored JSON round-trips to an equal record
        assertThat(webhooks.outboxFor(a.id())).hasSize(1);
        assertThat(repo.countByReceiptId(rid)).isEqualTo(1);
    }

    @Test void sameKeyDifferentBodyRejected() {
        String key = uniq("k");
        svc.create("r1", new Money(500, "USD"), key);
        assertThatThrownBy(() -> svc.create("r1", new Money(501, "USD"), key)).isInstanceOf(IdempotencyStore.KeyReuseException.class);
    }

    @Test void missingKeyRejected() {
        assertThatThrownBy(() -> svc.create("r1", new Money(500, "USD"), null)).hasMessageContaining("Idempotency-Key");
    }

    @Test void stateIsReadBackFromTheDatabase() {
        PaymentIntent pi = svc.create(uniq("r"), new Money(700, "INR"), uniq("k"));
        svc.authorize(pi.id());
        PaymentIntent fromDb = repo.findById(pi.id()).orElseThrow().toDomain();
        assertThat(fromDb.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(fromDb.amount()).isEqualTo(new Money(700, "INR"));
        assertThat(repo.findById(pi.id()).orElseThrow().version()).isEqualTo(1L); // insert=0, one UPDATE=1
    }

    @Test void failedTransitionRollsBackKeyAndEvent() {
        String id = svc.create(uniq("r"), new Money(1000, "USD"), uniq("c")).id();
        assertThatThrownBy(() -> svc.capture(id, "cap-early")).isInstanceOf(PaymentIntent.IllegalTransitionException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE scope = ?", Long.class, "capture:" + id)).isZero();
        svc.authorize(id);
        assertThat(svc.capture(id, "cap-early").status()).isEqualTo(PaymentStatus.CAPTURED); // key was freed by the rollback
        assertThat(webhooks.outboxFor(id)).extracting(WebhookPublisher.Sent::type)
                .containsExactly("payment_intent.created", "payment_intent.authorized", "payment_intent.captured");
    }

    @Test void retriedCaptureAndRefundDoNotDoubleApply() {
        String id = svc.create(uniq("r"), new Money(1000, "USD"), uniq("c")).id();
        svc.authorize(id);
        svc.capture(id, "cap-1");
        assertThat(svc.capture(id, "cap-1").status()).isEqualTo(PaymentStatus.CAPTURED); // retry: stored result, no 409
        svc.refund(id, 400, "ref-1");
        PaymentIntent again = svc.refund(id, 400, "ref-1");                              // retry: still 400 refunded, not 800
        assertThat(again.refunded().minor()).isEqualTo(400);
        assertThat(svc.get(id).refunded().minor()).isEqualTo(400);
        assertThat(svc.refund(id, 600, "ref-2").status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(webhooks.outboxFor(id)).extracting(WebhookPublisher.Sent::type).containsExactly(
                "payment_intent.created", "payment_intent.authorized", "payment_intent.captured",
                "payment_intent.refunded", "payment_intent.refunded");
    }

    @Test void concurrentRetriesOfOneCreateMakeOnePayment() throws Exception {
        String key = uniq("same-key"), rid = uniq("r");
        CountDownLatch go = new CountDownLatch(1);
        List<String> ids;
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 50).mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                await(go);
                return svc.create(rid, new Money(500, "USD"), key).id();
            }, pool)).toList();
            go.countDown();
            ids = futures.stream().map(CompletableFuture::join).distinct().toList();
        }
        assertThat(ids).hasSize(1);
        assertThat(repo.countByReceiptId(rid)).isEqualTo(1);           // one row in payment_intents
        assertThat(webhooks.outboxFor(ids.get(0))).hasSize(1);         // one created event
    }

    /** 20 concurrent refunds of 100 on a 1000 capture: row lock serializes them, exactly 10 win, never over-refunded. */
    @Test void concurrentRefundsNeverExceedCapturedAmount() throws Exception {
        String id = svc.create(uniq("r"), new Money(1000, "USD"), uniq("c")).id();
        svc.authorize(id);
        svc.capture(id, uniq("cap"));
        CountDownLatch go = new CountDownLatch(1);
        long ok;
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 20).mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                await(go);
                try { svc.refund(id, 100, "ref-" + i); return true; }
                catch (IllegalArgumentException | PaymentIntent.IllegalTransitionException e) { return false; }
            }, pool)).toList();
            go.countDown();
            ok = futures.stream().map(CompletableFuture::join).filter(b -> b).count();
        }
        assertThat(ok).isEqualTo(10);
        PaymentIntent end = svc.get(id);
        assertThat(end.refunded().minor()).isEqualTo(1000);
        assertThat(end.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(webhooks.outboxFor(id)).filteredOn(s -> s.type().equals("payment_intent.refunded")).hasSize(10);
    }

    @Test void unknownPayment() {
        assertThatThrownBy(() -> svc.authorize("nope")).isInstanceOf(PaymentService.NotFound.class);
    }

    /** Replay protection is in Postgres: a second verifier (think: app restarted) still rejects an id the first accepted. */
    @Test void replayRejectedAcrossVerifierInstances() {
        long t = Instant.now().getEpochSecond();
        Clock clock = Clock.fixed(Instant.ofEpochSecond(t), ZoneOffset.UTC);
        String body = "{\"id\":\"x\"}", header = WebhookSigner.header("sec", t, body), evt = uniq("evt");
        new WebhookVerifier("sec", Duration.ofMinutes(5), clock, SeenEventStore.jdbc(jdbc)).verify(header, body, evt);
        WebhookVerifier afterRestart = new WebhookVerifier("sec", Duration.ofMinutes(5), clock, SeenEventStore.jdbc(jdbc));
        assertThatThrownBy(() -> afterRestart.verify(header, body, evt)).isInstanceOf(WebhookVerifier.Rejected.class)
                .hasMessageContaining("replayed");
    }

    private static void await(CountDownLatch l) {
        try { l.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
