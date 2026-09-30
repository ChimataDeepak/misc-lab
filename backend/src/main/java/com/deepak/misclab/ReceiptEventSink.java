package com.deepak.misclab;

/** Extra destination for a newly created receipt (beyond the default AWS path). */
public interface ReceiptEventSink {
    void onReceiptCreated(Receipt receipt, String receiptJson);
}
