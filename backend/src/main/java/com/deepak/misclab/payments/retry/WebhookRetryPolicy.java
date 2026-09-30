package com.deepak.misclab.payments.retry;

import java.time.Duration;
import java.util.function.DoubleSupplier;

public final class WebhookRetryPolicy {
    public WebhookRetryPolicy(Duration base, Duration cap, int maxAttempts, DoubleSupplier random) {}
    public int maxAttempts() { throw new UnsupportedOperationException("TODO"); }
    public Duration backoff(int failedAttempts) { throw new UnsupportedOperationException("TODO"); }
}
