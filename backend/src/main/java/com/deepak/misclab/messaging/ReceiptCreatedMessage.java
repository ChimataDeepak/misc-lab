package com.deepak.misclab.messaging;

import java.math.BigDecimal;
import java.time.Instant;

/** Event contract on RabbitMQ. Only what consumers need; no internal fields. */
public record ReceiptCreatedMessage(String receiptId, String payer, String currency, BigDecimal usdAmount, Instant createdAt) {}
