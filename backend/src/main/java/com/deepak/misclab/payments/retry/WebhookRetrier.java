package com.deepak.misclab.payments.retry;

import java.time.Clock;
import java.util.function.Predicate;

/**
 * One relay step for one outbox event: if due, try to send; success/failure moves the state machine.
 * {@code sender} returns true on a 2xx; an exception counts as a failed attempt (it never escapes, the relay keeps
 * going with other events).
 */
public class WebhookRetrier {
    private final WebhookRetryPolicy policy;
    private final Predicate<String> sender;
    private final Clock clock;

    public WebhookRetrier(WebhookRetryPolicy policy, Predicate<String> sender, Clock clock) {
        this.policy = policy; this.sender = sender; this.clock = clock;
    }

    public WebhookDelivery attempt(WebhookDelivery d) {
        var now = clock.instant();
        if (!d.isDue(now)) return d;
        try {
            return sender.test(d.eventId()) ? d.onSuccess() : d.onFailure(now, "non-2xx response", policy);
        } catch (RuntimeException e) {
            return d.onFailure(now, e.getClass().getSimpleName() + ": " + e.getMessage(), policy);
        }
    }
}
