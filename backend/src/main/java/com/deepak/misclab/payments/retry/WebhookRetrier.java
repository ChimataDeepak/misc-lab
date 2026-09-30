package com.deepak.misclab.payments.retry;

import java.time.Clock;
import java.util.function.Predicate;

public class WebhookRetrier {
    public WebhookRetrier(WebhookRetryPolicy policy, Predicate<String> sender, Clock clock) {}
    public WebhookDelivery attempt(WebhookDelivery d) { throw new UnsupportedOperationException("TODO"); }
}
