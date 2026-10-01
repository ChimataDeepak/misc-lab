package com.deepak.misclab.statement;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Response DTO for GET /api/statements/{payerId}. Plain records: no lazy proxies reach Jackson. */
public record Statement(long payerId, int receiptCount, Map<String, Long> totalMinorByCurrency, List<Item> receipts) {
    public record Item(long id, String merchant, long amountMinor, String currency, Instant createdAt, List<Line> lines) {}
    public record Line(String description, long amountMinor) {}
}
