package com.deepak.misclab.payments;

import java.util.Currency;

/**
 * Money as integer minor units (paise, cents) plus an ISO-4217 currency. No floating point anywhere:
 * 0.1 + 0.2 != 0.3 in binary floats, and payments must add up exactly.
 */
public record Money(long minor, String currency) {
    public Money {
        if (minor < 0) throw new IllegalArgumentException("amount must be >= 0 minor units");
        if (currency == null) throw new IllegalArgumentException("currency is required");
        currency = Currency.getInstance(currency.toUpperCase()).getCurrencyCode(); // throws on unknown code
    }

    public Money plus(Money o) { same(o); return new Money(Math.addExact(minor, o.minor), currency); }
    public Money minus(Money o) { same(o); return new Money(Math.subtractExact(minor, o.minor), currency); }
    public boolean greaterThan(Money o) { same(o); return minor > o.minor; }

    private void same(Money o) {
        if (!currency.equals(o.currency)) throw new IllegalArgumentException("currency mismatch: " + currency + " vs " + o.currency);
    }
}
