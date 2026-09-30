package com.deepak.misclab;

/** GraphQL-safe view: money as strings, no BigDecimal scalar. */
public record ReceiptView(String receiptId, String payer, String amount, String currency,
                          String usdAmount, String createdAt) {
    static ReceiptView from(Receipt r) {
        return new ReceiptView(
                r.receiptId(),
                r.payer(),
                r.amount().toPlainString(),
                r.currency(),
                r.usdAmount().toPlainString(),
                r.createdAt().toString());
    }
}
