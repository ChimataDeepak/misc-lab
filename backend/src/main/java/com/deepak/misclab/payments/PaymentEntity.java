package com.deepak.misclab.payments;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA row for {@link PaymentIntent}. The domain record stays immutable and owns the state-machine rules; this class
 * is only the persistence shape. {@code @Version} makes Hibernate add {@code WHERE version = ?} to every UPDATE, so a
 * lost update (two writers both starting from version 3) fails loudly instead of silently overwriting.
 */
@Entity
@Table(name = "payment_intents")
public class PaymentEntity {
    @Id private String id;
    @Column(name = "receipt_id", nullable = false) private String receiptId;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(name = "captured_minor", nullable = false) private long capturedMinor;
    @Column(name = "refunded_minor", nullable = false) private long refundedMinor;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentStatus status;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;   // null = not yet persisted (Spring Data uses that to choose persist vs merge)

    protected PaymentEntity() {}

    static PaymentEntity from(PaymentIntent pi) {
        PaymentEntity e = new PaymentEntity();
        e.id = pi.id(); e.receiptId = pi.receiptId();
        e.apply(pi);
        return e;
    }

    /** Copies the new state produced by the domain transition onto the managed row. */
    void apply(PaymentIntent pi) {
        currency = pi.amount().currency(); amountMinor = pi.amount().minor();
        capturedMinor = pi.captured().minor(); refundedMinor = pi.refunded().minor();
        status = pi.status(); updatedAt = pi.updatedAt();
    }

    PaymentIntent toDomain() {
        return new PaymentIntent(id, receiptId, new Money(amountMinor, currency), new Money(capturedMinor, currency),
                new Money(refundedMinor, currency), status, updatedAt);
    }

    Long version() { return version; }
}
