package com.deepak.misclab;

import java.math.BigDecimal;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/** DynamoDB item mapped by the Enhanced Client (needs a mutable bean + no-arg ctor). */
@DynamoDbBean
public class ReceiptItem {
    private String receiptId;
    private String payer;
    private BigDecimal amount;
    private String currency;
    private BigDecimal usdAmount;
    private String createdAt;

    public ReceiptItem() { }

    public static ReceiptItem from(Receipt r) {
        ReceiptItem i = new ReceiptItem();
        i.receiptId = r.receiptId(); i.payer = r.payer(); i.amount = r.amount(); i.currency = r.currency();
        i.usdAmount = r.usdAmount(); i.createdAt = r.createdAt().toString();
        return i;
    }

    public Receipt toReceipt() {
        return new Receipt(receiptId, payer, amount, currency, usdAmount, java.time.Instant.parse(createdAt));
    }

    @DynamoDbPartitionKey
    public String getReceiptId() { return receiptId; }
    public void setReceiptId(String v) { receiptId = v; }
    public String getPayer() { return payer; }
    public void setPayer(String v) { payer = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { amount = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { currency = v; }
    public BigDecimal getUsdAmount() { return usdAmount; }
    public void setUsdAmount(BigDecimal v) { usdAmount = v; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String v) { createdAt = v; }
}
