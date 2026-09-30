package com.deepak.misclab.gcp;

import com.deepak.misclab.Receipt;
import com.deepak.misclab.ReceiptEventSink;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * GCP twin of the AWS path: receipt JSON archived to Cloud Storage (like S3), and a ReceiptCreated event published
 * to Pub/Sub (like SQS). Pub/Sub attributes carry the event type and receipt id so subscribers can filter without
 * parsing the body; the receipt id is also the ordering-free dedupe key for consumers (Pub/Sub is at-least-once).
 */
public class GcpReceiptSink implements ReceiptEventSink {
    private final Publisher publisher;
    private final Storage storage;
    private final String bucket;

    public GcpReceiptSink(Publisher publisher, Storage storage, String bucket) {
        this.publisher = publisher; this.storage = storage; this.bucket = bucket;
    }

    @Override
    public void onReceiptCreated(Receipt r, String receiptJson) {
        storage.create(BlobInfo.newBuilder(BlobId.of(bucket, objectName(r.receiptId()))).setContentType("application/json").build(),
                receiptJson.getBytes(StandardCharsets.UTF_8));
        String event = "{\"type\":\"ReceiptCreated\",\"receiptId\":\"" + r.receiptId() + "\",\"usdAmount\":" + r.usdAmount() + "}";
        PubsubMessage msg = PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8(event))
                .putAttributes("type", "ReceiptCreated").putAttributes("receiptId", r.receiptId()).build();
        try {
            // publish() is async and batched; waiting here keeps "receipt created" == "event accepted by Pub/Sub".
            publisher.publish(msg).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted publishing ReceiptCreated", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Pub/Sub publish failed for " + r.receiptId(), e);
        }
    }

    public static String objectName(String receiptId) { return "receipts/" + receiptId + ".json"; }
}
