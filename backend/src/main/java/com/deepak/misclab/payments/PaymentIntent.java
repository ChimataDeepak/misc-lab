package com.deepak.misclab.payments;

import java.time.Instant;

/** Immutable snapshot of a payment; every state change produces a new instance (easy to reason about, easy to store). */
public record PaymentIntent(String id, String receiptId, Money amount, Money captured, Money refunded,
                            PaymentStatus status, Instant updatedAt) {

    public static PaymentIntent create(String id, String receiptId, Money amount, Instant now) {
        if (amount.minor() == 0) throw new IllegalArgumentException("amount must be > 0");
        Money zero = new Money(0, amount.currency());
        return new PaymentIntent(id, receiptId, amount, zero, zero, PaymentStatus.CREATED, now);
    }

    public PaymentIntent authorize(Instant now) { return move(PaymentStatus.AUTHORIZED, captured, refunded, now); }

    public PaymentIntent voidIt(Instant now) { return move(PaymentStatus.VOIDED, captured, refunded, now); }

    /** Captures the full authorized amount (single capture keeps the model small). */
    public PaymentIntent capture(Instant now) { return move(PaymentStatus.CAPTURED, amount, refunded, now); }

    /** Refunds part or all of what was captured; never more than captured minus already refunded. */
    public PaymentIntent refund(Money howMuch, Instant now) {
        // state check first: refunding a VOIDED/REFUNDED payment is a 409, not a "bad amount" 400
        if (!status.canMoveTo(PaymentStatus.REFUNDED)) throw new IllegalTransitionException(status, PaymentStatus.REFUNDED);
        if (howMuch.minor() == 0) throw new IllegalArgumentException("refund must be > 0");
        Money total = refunded.plus(howMuch);
        if (total.greaterThan(captured)) {
            throw new IllegalArgumentException("refund exceeds captured amount: refundable="
                    + captured.minus(refunded).minor() + " requested=" + howMuch.minor());
        }
        PaymentStatus target = total.minor() == captured.minor() ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        return move(target, captured, total, now);
    }

    private PaymentIntent move(PaymentStatus target, Money cap, Money ref, Instant now) {
        if (!status.canMoveTo(target)) throw new IllegalTransitionException(status, target);
        return new PaymentIntent(id, receiptId, amount, cap, ref, target, now);
    }

    public static class IllegalTransitionException extends RuntimeException {
        public IllegalTransitionException(PaymentStatus from, PaymentStatus to) {
            super("illegal transition " + from + " -> " + to);
        }
    }
}
