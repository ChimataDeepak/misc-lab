package com.deepak.misclab.payments;

import java.util.EnumSet;
import java.util.Set;

/**
 * Payment lifecycle. The allowed-transition table lives here so the rule is in one place:
 * CREATED -> AUTHORIZED -> CAPTURED -> (PARTIALLY_REFUNDED ->)* REFUNDED, and CREATED/AUTHORIZED -> VOIDED.
 * Once money is captured it can only move back via refunds; a void is only possible before capture.
 */
public enum PaymentStatus {
    CREATED, AUTHORIZED, CAPTURED, PARTIALLY_REFUNDED, REFUNDED, VOIDED;

    public Set<PaymentStatus> next() {
        return switch (this) {
            case CREATED -> EnumSet.of(AUTHORIZED, VOIDED);
            case AUTHORIZED -> EnumSet.of(CAPTURED, VOIDED);
            case CAPTURED, PARTIALLY_REFUNDED -> EnumSet.of(PARTIALLY_REFUNDED, REFUNDED);
            case REFUNDED, VOIDED -> EnumSet.noneOf(PaymentStatus.class);
        };
    }

    public boolean canMoveTo(PaymentStatus target) { return next().contains(target); }
}
