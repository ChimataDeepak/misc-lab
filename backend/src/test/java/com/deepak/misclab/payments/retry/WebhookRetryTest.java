package com.deepak.misclab.payments.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Written test-first (see README "TDD: webhook retry"): these tests were committed failing against empty stubs,
 * then the implementation made them pass.
 */
class WebhookRetryTest {
    static final Instant T0 = Instant.parse("2026-09-30T10:00:00Z");

    static WebhookRetryPolicy policy(double random) {
        return new WebhookRetryPolicy(Duration.ofSeconds(1), Duration.ofSeconds(30), 5, () -> random);
    }

    @Test void backoffDoublesPerFailedAttemptWithoutJitter() {
        WebhookRetryPolicy p = policy(1.0);                    // random=1.0 -> the full exponential ceiling
        assertThat(p.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(p.backoff(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(p.backoff(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(p.backoff(4)).isEqualTo(Duration.ofSeconds(8));
    }

    @Test void backoffIsCappedAndDoesNotOverflow() {
        WebhookRetryPolicy p = policy(1.0);
        assertThat(p.backoff(6)).isEqualTo(Duration.ofSeconds(30));   // 32 s capped to 30 s
        assertThat(p.backoff(200)).isEqualTo(Duration.ofSeconds(30)); // 2^199 must not overflow to negative
    }

    @Test void fullJitterPicksUniformlyBelowTheCeiling() {
        assertThat(policy(0.5).backoff(3)).isEqualTo(Duration.ofSeconds(2));  // half of 4 s
        assertThat(policy(0.0).backoff(3)).isEqualTo(Duration.ZERO);
    }

    @Test void rejectsNonPositiveAttemptNumbers() {
        assertThatThrownBy(() -> policy(1.0).backoff(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void newDeliveryIsDueImmediately() {
        WebhookDelivery d = WebhookDelivery.pending("evt_1", T0);
        assertThat(d.status()).isEqualTo(WebhookDelivery.Status.PENDING);
        assertThat(d.attempts()).isZero();
        assertThat(d.isDue(T0)).isTrue();
    }

    @Test void failureSchedulesNextAttemptWithBackoff() {
        WebhookDelivery d = WebhookDelivery.pending("evt_1", T0).onFailure(T0, "HTTP 500", policy(1.0));
        assertThat(d.status()).isEqualTo(WebhookDelivery.Status.PENDING);
        assertThat(d.attempts()).isEqualTo(1);
        assertThat(d.nextAttemptAt()).isEqualTo(T0.plusSeconds(1));
        assertThat(d.lastError()).isEqualTo("HTTP 500");
        assertThat(d.isDue(T0)).isFalse();
        assertThat(d.isDue(T0.plusSeconds(1))).isTrue();
    }

    @Test void reachingMaxAttemptsMovesToDeadLetter() {
        WebhookRetryPolicy p = policy(1.0);
        WebhookDelivery d = WebhookDelivery.pending("evt_1", T0);
        for (int i = 0; i < 5; i++) d = d.onFailure(T0, "timeout", p);
        assertThat(d.status()).isEqualTo(WebhookDelivery.Status.DEAD_LETTER);
        assertThat(d.attempts()).isEqualTo(5);
        assertThat(d.nextAttemptAt()).isNull();
        assertThat(d.isDue(T0.plusSeconds(3600))).isFalse();       // never retried again
    }

    @Test void successIsTerminal() {
        WebhookDelivery d = WebhookDelivery.pending("evt_1", T0).onFailure(T0, "x", policy(1.0)).onSuccess();
        assertThat(d.status()).isEqualTo(WebhookDelivery.Status.DELIVERED);
        assertThat(d.attempts()).isEqualTo(2);
        assertThat(d.isDue(T0.plusSeconds(3600))).isFalse();
    }

    @Test void retrierSendsOnlyWhenDueAndCountsExceptionsAsFailures() {
        List<String> sent = new ArrayList<>();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        WebhookRetrier retrier = new WebhookRetrier(policy(1.0), id -> { sent.add(id); throw new IllegalStateException("conn refused"); }, clock);

        WebhookDelivery d = retrier.attempt(WebhookDelivery.pending("evt_1", T0));
        assertThat(sent).containsExactly("evt_1");
        assertThat(d.attempts()).isEqualTo(1);
        assertThat(d.lastError()).contains("conn refused");

        WebhookDelivery again = retrier.attempt(d);                // not due yet (next at T0+1s)
        assertThat(sent).hasSize(1);
        assertThat(again).isEqualTo(d);
    }

    @Test void retrierDeliversAfterTransientFailures() {
        int[] calls = {0};
        WebhookRetryPolicy p = policy(1.0);
        Instant[] now = {T0};
        Clock clock = new Clock() {
            public ZoneOffset getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return now[0]; }
        };
        WebhookRetrier retrier = new WebhookRetrier(p, id -> ++calls[0] >= 3, clock); // 2 failures (false), then OK
        WebhookDelivery d = WebhookDelivery.pending("evt_1", T0);
        while (d.status() == WebhookDelivery.Status.PENDING) {
            d = retrier.attempt(d);
            if (d.nextAttemptAt() != null) now[0] = d.nextAttemptAt();
        }
        assertThat(d.status()).isEqualTo(WebhookDelivery.Status.DELIVERED);
        assertThat(d.attempts()).isEqualTo(3);
        assertThat(now[0]).isEqualTo(T0.plusSeconds(1 + 2));       // waited 1 s, then 2 s
    }
}
