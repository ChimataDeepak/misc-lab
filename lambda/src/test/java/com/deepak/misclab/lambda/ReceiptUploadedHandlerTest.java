package com.deepak.misclab.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReceiptUploadedHandlerTest {

    private static S3EventNotificationRecord record(String key, long size) {
        var entity = new S3Entity("cfg", new S3BucketEntity("misc-receipts", null, "arn:aws:s3:::misc-receipts"),
                new S3ObjectEntity(key, size, "etag", "v1", "seq"), "1.0");
        return new S3EventNotificationRecord("ap-south-1", "ObjectCreated:Put", "aws:s3", "2026-09-30T00:00:00.000Z",
                "2.1", null, null, entity, null);
    }

    @Test
    void summarisesUploadedReceipts() {
        S3Event event = new S3Event(List.of(record("receipts/a.json", 100), record("receipts/b.json", 50)));
        assertEquals("processed 2 object(s), 150 bytes: receipts/a.json,receipts/b.json",
                new ReceiptUploadedHandler().handleRequest(event, null));
    }
}
