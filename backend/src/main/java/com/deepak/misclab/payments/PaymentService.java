package com.deepak.misclab.payments;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Orchestrates the state machine, idempotency and webhooks. In-memory store for the lab. */
public class PaymentService {
    private final Map<String, PaymentIntent> store = new ConcurrentHashMap<>();
    private final IdempotencyStore idempotency;
    private final WebhookPublisher webhooks;
    private final Clock clock;

    public PaymentService(IdempotencyStore idempotency, WebhookPublisher webhooks, Clock clock) {
        this.idempotency = idempotency; this.webhooks = webhooks; this.clock = clock;
    }

    public PaymentIntent create(String receiptId, Money amount, String idemKey) {
        String fp = receiptId + "|" + amount.minor() + "|" + amount.currency();
        return idempotency.execute("create", idemKey, fp, () -> {
            PaymentIntent pi = PaymentIntent.create("pi_" + UUID.randomUUID(), receiptId, amount, clock.instant());
            store.put(pi.id(), pi);
            webhooks.publish("payment_intent.created", pi);
            return pi;
        });
    }

    public PaymentIntent authorize(String id) { return change(id, "payment_intent.authorized", pi -> pi.authorize(clock.instant())); }

    public PaymentIntent voidIt(String id) { return change(id, "payment_intent.voided", pi -> pi.voidIt(clock.instant())); }

    public PaymentIntent capture(String id, String idemKey) {
        return idempotency.execute("capture:" + id, idemKey, id,
                () -> change(id, "payment_intent.captured", pi -> pi.capture(clock.instant())));
    }

    public PaymentIntent refund(String id, long amountMinor, String idemKey) {
        return idempotency.execute("refund:" + id, idemKey, id + "|" + amountMinor, () -> change(id, "payment_intent.refunded",
                pi -> pi.refund(new Money(amountMinor, pi.amount().currency()), clock.instant())));
    }

    public PaymentIntent get(String id) {
        PaymentIntent pi = store.get(id);
        if (pi == null) throw new NotFound(id);
        return pi;
    }

    /** compute() holds the map bin lock, so two concurrent transitions on one payment serialize instead of racing. */
    private PaymentIntent change(String id, String eventType, java.util.function.UnaryOperator<PaymentIntent> f) {
        PaymentIntent updated = store.compute(id, (k, cur) -> {
            if (cur == null) throw new NotFound(id);
            return f.apply(cur);
        });
        webhooks.publish(eventType, updated);
        return updated;
    }

    public static class NotFound extends RuntimeException {
        public NotFound(String id) { super("payment not found: " + id); }
    }
}
