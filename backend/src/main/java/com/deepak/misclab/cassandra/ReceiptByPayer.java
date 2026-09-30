package com.deepak.misclab.cassandra;

import com.deepak.misclab.Receipt;
import java.math.BigDecimal;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("receipts_by_payer")
public record ReceiptByPayer(
        @PrimaryKey ReceiptByPayerKey key,
        @Column("amount") BigDecimal amount,
        @Column("currency") String currency,
        @Column("usd_amount") BigDecimal usdAmount) {

    public static ReceiptByPayer from(Receipt r) {
        return new ReceiptByPayer(new ReceiptByPayerKey(r.payer(), r.createdAt(), r.receiptId()), r.amount(), r.currency(), r.usdAmount());
    }
}
