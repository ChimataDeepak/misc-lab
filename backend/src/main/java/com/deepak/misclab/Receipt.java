package com.deepak.misclab;

import com.deepak.misclab.validation.IsoCurrency;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record Receipt(String receiptId, String payer, BigDecimal amount, String currency,
                      BigDecimal usdAmount, Instant createdAt) {
    /** Validated with Jakarta Bean Validation when it arrives on POST /api/receipts (@Valid in ReceiptController). */
    public record Request(@NotBlank @Size(max = 100) String payer,
                          @NotNull @Positive @DecimalMax("1000000000") BigDecimal amount,
                          @NotBlank @IsoCurrency String currency) {}
}
