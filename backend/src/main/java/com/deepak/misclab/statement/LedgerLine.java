package com.deepak.misclab.statement;

import jakarta.persistence.*;

/** One line item of a ledger receipt (table ledger_receipt_lines). */
@Entity
@Table(name = "ledger_receipt_lines")
public class LedgerLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "receipt_id") private LedgerReceipt receipt;
    @Column(nullable = false) private String description;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;

    protected LedgerLine() { }

    LedgerLine(LedgerReceipt receipt, String description, long amountMinor) {
        this.receipt = receipt; this.description = description; this.amountMinor = amountMinor;
    }

    public Long getId() { return id; }
    public String getDescription() { return description; }
    public long getAmountMinor() { return amountMinor; }
}
