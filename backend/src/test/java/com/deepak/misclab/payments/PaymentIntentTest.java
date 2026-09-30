package com.deepak.misclab.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PaymentIntentTest {
    static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    static final Money INR_1000 = new Money(100_000, "INR"); // 1000.00 INR in paise

    static PaymentIntent created() { return PaymentIntent.create("pi_1", "r1", INR_1000, NOW); }

    /** The full table: every (from, to) pair is either explicitly allowed or rejected. */
    static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
            PaymentStatus.CREATED, EnumSet.of(PaymentStatus.AUTHORIZED, PaymentStatus.VOIDED),
            PaymentStatus.AUTHORIZED, EnumSet.of(PaymentStatus.CAPTURED, PaymentStatus.VOIDED),
            PaymentStatus.CAPTURED, EnumSet.of(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED),
            PaymentStatus.PARTIALLY_REFUNDED, EnumSet.of(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED),
            PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class),
            PaymentStatus.VOIDED, EnumSet.noneOf(PaymentStatus.class));

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void transitionTableMatchesSpec(PaymentStatus from) {
        for (PaymentStatus to : PaymentStatus.values()) {
            assertThat(from.canMoveTo(to)).as(from + " -> " + to).isEqualTo(ALLOWED.get(from).contains(to));
        }
    }

    @Test void createAuthorizeCapture() {
        PaymentIntent pi = created().authorize(NOW).capture(NOW);
        assertThat(pi.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(pi.captured()).isEqualTo(INR_1000);
    }

    @Test void voidFromCreatedAndFromAuthorized() {
        assertThat(created().voidIt(NOW).status()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(created().authorize(NOW).voidIt(NOW).status()).isEqualTo(PaymentStatus.VOIDED);
    }

    @Test void partialThenFullRefund() {
        PaymentIntent pi = created().authorize(NOW).capture(NOW).refund(new Money(30_000, "INR"), NOW);
        assertThat(pi.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        pi = pi.refund(new Money(70_000, "INR"), NOW);
        assertThat(pi.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(pi.refunded().minor()).isEqualTo(100_000);
    }

    @Test void singleFullRefund() {
        assertThat(created().authorize(NOW).capture(NOW).refund(INR_1000, NOW).status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test void refundMoreThanCapturedRejected() {
        PaymentIntent pi = created().authorize(NOW).capture(NOW).refund(new Money(90_000, "INR"), NOW);
        assertThatThrownBy(() -> pi.refund(new Money(10_001, "INR"), NOW)).hasMessageContaining("refundable=10000");
    }

    @Test void illegalTransitionsRejected() {
        assertThatThrownBy(() -> created().capture(NOW)).isInstanceOf(PaymentIntent.IllegalTransitionException.class)
                .hasMessage("illegal transition CREATED -> CAPTURED");
        assertThatThrownBy(() -> created().refund(INR_1000, NOW)).isInstanceOf(PaymentIntent.IllegalTransitionException.class);
        assertThatThrownBy(() -> created().authorize(NOW).capture(NOW).voidIt(NOW)).isInstanceOf(PaymentIntent.IllegalTransitionException.class);
        assertThatThrownBy(() -> created().voidIt(NOW).authorize(NOW)).isInstanceOf(PaymentIntent.IllegalTransitionException.class);
        assertThatThrownBy(() -> created().authorize(NOW).authorize(NOW)).isInstanceOf(PaymentIntent.IllegalTransitionException.class);
        assertThatThrownBy(() -> created().authorize(NOW).capture(NOW).refund(INR_1000, NOW).refund(new Money(1, "INR"), NOW))
                .isInstanceOf(PaymentIntent.IllegalTransitionException.class);
    }

    @Test void moneyRules() {
        assertThatThrownBy(() -> new Money(-1, "INR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(1, "XYZ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(1, "INR").plus(new Money(1, "USD"))).hasMessageContaining("currency mismatch");
        assertThat(new Money(5, "usd").currency()).isEqualTo("USD");
        assertThatThrownBy(() -> PaymentIntent.create("p", "r", new Money(0, "INR"), NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> created().authorize(NOW).capture(NOW).refund(new Money(1, "USD"), NOW)).hasMessageContaining("currency mismatch");
    }
}
