package com.deepak.misclab.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {
    Clock clock = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);
    WebhookPublisher webhooks = new WebhookPublisher("s", "", clock, new ObjectMapper().findAndRegisterModules());
    PaymentService svc = new PaymentService(new IdempotencyStore(), webhooks, clock);

    @Test void sameKeySameBodyReturnsSamePaymentAndNoSecondWebhook() {
        PaymentIntent a = svc.create("r1", new Money(500, "USD"), "k1");
        PaymentIntent b = svc.create("r1", new Money(500, "USD"), "k1");
        assertThat(b.id()).isEqualTo(a.id());
        assertThat(webhooks.outbox()).hasSize(1);
    }

    @Test void sameKeyDifferentBodyRejected() {
        svc.create("r1", new Money(500, "USD"), "k1");
        assertThatThrownBy(() -> svc.create("r1", new Money(501, "USD"), "k1")).isInstanceOf(IdempotencyStore.KeyReuseException.class);
    }

    @Test void missingKeyRejected() {
        assertThatThrownBy(() -> svc.create("r1", new Money(500, "USD"), null)).hasMessageContaining("Idempotency-Key");
    }

    @Test void retriedCaptureAndRefundDoNotDoubleApply() {
        String id = svc.create("r1", new Money(1000, "USD"), "c").id();
        svc.authorize(id);
        svc.capture(id, "cap-1");
        assertThat(svc.capture(id, "cap-1").status()).isEqualTo(PaymentStatus.CAPTURED); // retry: stored result, no 409
        svc.refund(id, 400, "ref-1");
        PaymentIntent again = svc.refund(id, 400, "ref-1");                              // retry: still 400 refunded, not 800
        assertThat(again.refunded().minor()).isEqualTo(400);
        assertThat(svc.get(id).refunded().minor()).isEqualTo(400);
        assertThat(svc.refund(id, 600, "ref-2").status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(webhooks.outbox()).extracting(s -> s.event().type()).containsExactly(
                "payment_intent.created", "payment_intent.authorized", "payment_intent.captured",
                "payment_intent.refunded", "payment_intent.refunded");
    }

    @Test void concurrentRetriesOfOneCreateMakeOnePayment() throws Exception {
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 50).mapToObj(i -> CompletableFuture.supplyAsync(
                    () -> svc.create("r1", new Money(500, "USD"), "same-key").id(), pool)).toList();
            assertThat(futures.stream().map(CompletableFuture::join).distinct()).hasSize(1);
        }
        assertThat(webhooks.outbox()).hasSize(1);
    }

    @Test void unknownPayment() {
        assertThatThrownBy(() -> svc.authorize("nope")).isInstanceOf(PaymentService.NotFound.class);
    }
}
