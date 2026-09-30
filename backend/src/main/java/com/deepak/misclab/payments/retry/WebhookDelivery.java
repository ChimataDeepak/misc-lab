package com.deepak.misclab.payments.retry;

import java.time.Instant;

public record WebhookDelivery(String eventId, Status status, int attempts, Instant nextAttemptAt, String lastError) {
    public enum Status { PENDING, DELIVERED, DEAD_LETTER }
    public static WebhookDelivery pending(String eventId, Instant now) { throw new UnsupportedOperationException("TODO"); }
    public boolean isDue(Instant now) { throw new UnsupportedOperationException("TODO"); }
    public WebhookDelivery onSuccess() { throw new UnsupportedOperationException("TODO"); }
    public WebhookDelivery onFailure(Instant now, String error, WebhookRetryPolicy policy) { throw new UnsupportedOperationException("TODO"); }
}
