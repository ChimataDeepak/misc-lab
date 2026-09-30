package com.deepak.misclab.payments;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates the state machine, idempotency and webhooks on PostgreSQL. Each public call is one transaction:
 * idempotency-key claim + row change + outbox event commit together or not at all.
 * Concurrency: transitions lock the row with SELECT ... FOR UPDATE (writers on one payment serialize, each sees the
 * previous writer's committed state); {@code @Version} on the entity is a second guard against lost updates.
 */
public class PaymentService {
    private final PaymentRepository repo;
    private final IdempotencyStore idempotency;
    private final WebhookPublisher webhooks;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentService(PaymentRepository repo, IdempotencyStore idempotency, WebhookPublisher webhooks,
                          TransactionTemplate tx, Clock clock) {
        this.repo = repo; this.idempotency = idempotency; this.webhooks = webhooks; this.tx = tx; this.clock = clock;
    }

    public PaymentIntent create(String receiptId, Money amount, String idemKey) {
        String fp = receiptId + "|" + amount.minor() + "|" + amount.currency();
        return tx.execute(s -> idempotency.execute("create", idemKey, fp, PaymentIntent.class, () -> {
            PaymentIntent pi = PaymentIntent.create("pi_" + UUID.randomUUID(), receiptId, amount, now());
            repo.saveAndFlush(PaymentEntity.from(pi)); // flush: the outbox row below has a FK to this id
            webhooks.publish("payment_intent.created", pi);
            return pi;
        }));
    }

    public PaymentIntent authorize(String id) {
        return tx.execute(s -> change(id, "payment_intent.authorized", pi -> pi.authorize(now())));
    }

    public PaymentIntent voidIt(String id) {
        return tx.execute(s -> change(id, "payment_intent.voided", pi -> pi.voidIt(now())));
    }

    public PaymentIntent capture(String id, String idemKey) {
        return tx.execute(s -> idempotency.execute("capture:" + id, idemKey, id, PaymentIntent.class,
                () -> change(id, "payment_intent.captured", pi -> pi.capture(now()))));
    }

    public PaymentIntent refund(String id, long amountMinor, String idemKey) {
        return tx.execute(s -> idempotency.execute("refund:" + id, idemKey, id + "|" + amountMinor, PaymentIntent.class,
                () -> change(id, "payment_intent.refunded",
                        pi -> pi.refund(new Money(amountMinor, pi.amount().currency()), now()))));
    }

    public PaymentIntent get(String id) {
        return repo.findById(id).map(PaymentEntity::toDomain).orElseThrow(() -> new NotFound(id));
    }

    /** Must run inside a transaction: lock the row, apply the domain transition, write it back, add the outbox event. */
    private PaymentIntent change(String id, String eventType, UnaryOperator<PaymentIntent> f) {
        PaymentEntity row = repo.findByIdForUpdate(id).orElseThrow(() -> new NotFound(id));
        PaymentIntent updated = f.apply(row.toDomain());  // throws IllegalTransition / IllegalArgument -> rollback
        row.apply(updated);
        repo.saveAndFlush(row);                           // UPDATE ... WHERE id=? AND version=?
        webhooks.publish(eventType, updated);
        return updated;
    }

    /** Postgres TIMESTAMPTZ keeps microseconds; truncating here makes the returned object equal to what a later GET reads. */
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }

    public static class NotFound extends RuntimeException {
        public NotFound(String id) { super("payment not found: " + id); }
    }
}
