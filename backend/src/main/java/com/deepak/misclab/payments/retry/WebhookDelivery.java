package com.deepak.misclab.payments.retry;

import java.time.Instant;

/**
 * Delivery state of one outbox webhook event. Immutable: every transition returns a new value, so the state machine
 * is easy to test and to persist as one row (status, attempts, next_attempt_at, last_error).
 * PENDING -> DELIVERED on success; PENDING -> PENDING (later next attempt) on failure; after maxAttempts failures
 * -> DEAD_LETTER, which is terminal and needs a human/replay tool.
 */
public record WebhookDelivery(String eventId, Status status, int attempts, Instant nextAttemptAt, String lastError) {
    public enum Status { PENDING, DELIVERED, DEAD_LETTER }

    public static WebhookDelivery pending(String eventId, Instant now) {
        return new WebhookDelivery(eventId, Status.PENDING, 0, now, null);
    }

    public boolean isDue(Instant now) {
        return status == Status.PENDING && !now.isBefore(nextAttemptAt);
    }

    public WebhookDelivery onSuccess() {
        requirePending();
        return new WebhookDelivery(eventId, Status.DELIVERED, attempts + 1, null, lastError);
    }

    public WebhookDelivery onFailure(Instant now, String error, WebhookRetryPolicy policy) {
        requirePending();
        int n = attempts + 1;
        if (n >= policy.maxAttempts()) return new WebhookDelivery(eventId, Status.DEAD_LETTER, n, null, error);
        return new WebhookDelivery(eventId, Status.PENDING, n, now.plus(policy.backoff(n)), error);
    }

    private void requirePending() {
        if (status != Status.PENDING) throw new IllegalStateException("delivery " + eventId + " is " + status);
    }
}
