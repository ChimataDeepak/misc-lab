package com.deepak.misclab;

import java.math.BigDecimal;
import java.time.Instant;

public record Receipt(String receiptId, String payer, BigDecimal amount, String currency,
                      BigDecimal usdAmount, Instant createdAt) {
    public record Request(String payer, BigDecimal amount, String currency) {}
}
