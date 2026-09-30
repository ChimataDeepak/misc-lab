package com.deepak.misclab.payments.retry;

import java.time.Duration;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Exponential backoff with <b>full jitter</b>: after the n-th failed attempt, wait a uniformly random time in
 * [0, min(cap, base * 2^(n-1))). Jitter spreads retries from many merchants' failed webhooks so they don't hit the
 * receiver again in the same instant (thundering herd). {@code random} is injected so tests are deterministic.
 */
public final class WebhookRetryPolicy {
    private final Duration base;
    private final Duration cap;
    private final int maxAttempts;
    private final DoubleSupplier random;

    public WebhookRetryPolicy(Duration base, Duration cap, int maxAttempts, DoubleSupplier random) {
        if (base.isNegative() || base.isZero() || cap.compareTo(base) < 0) throw new IllegalArgumentException("need 0 < base <= cap");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        this.base = base; this.cap = cap; this.maxAttempts = maxAttempts; this.random = Objects.requireNonNull(random);
    }

    public int maxAttempts() { return maxAttempts; }

    public Duration backoff(int failedAttempts) {
        if (failedAttempts < 1) throw new IllegalArgumentException("failedAttempts must be >= 1");
        long capMs = cap.toMillis();
        // shift only while it stays below the cap: avoids 2^n overflow for large n
        int shift = Math.min(failedAttempts - 1, 62);
        long ceilingMs = base.toMillis() > (capMs >> shift) ? capMs : Math.min(capMs, base.toMillis() << shift);
        return Duration.ofMillis((long) (random.getAsDouble() * ceilingMs));
    }
}
