package com.deepak.misclab.statement;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A receipt in the relational ledger (table ledger_receipts). Lines are LAZY: loaded only when touched. */
@Entity
@Table(name = "ledger_receipts")
public class LedgerReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "payer_id", nullable = false) private long payerId;
    @Column(nullable = false) private String merchant;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false) private String currency;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @OneToMany(mappedBy = "receipt", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<LedgerLine> lines = new ArrayList<>();

    protected LedgerReceipt() { }

    public LedgerReceipt(long payerId, String merchant, String currency, Instant createdAt) {
        this.payerId = payerId; this.merchant = merchant; this.currency = currency; this.createdAt = createdAt;
    }

    public void addLine(String description, long amountMinor) {
        lines.add(new LedgerLine(this, description, amountMinor));
        this.amountMinor += amountMinor;
    }

    public Long getId() { return id; }
    public long getPayerId() { return payerId; }
    public String getMerchant() { return merchant; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public List<LedgerLine> getLines() { return lines; }
}
