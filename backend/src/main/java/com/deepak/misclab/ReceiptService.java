package com.deepak.misclab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

@Service
public class ReceiptService {
    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

    private final FxClient fx;
    private final DynamoDbTable<ReceiptItem> table;
    private final S3Client s3;
    private final SqsClient sqs;
    private final AuditLogRepository audit;
    private final AwsProps props;
    private final ObjectMapper json;
    private final Counter created;
    private final List<ReceiptEventSink> extraSinks;
    private final ExecutorService io;
    private volatile String queueUrl;

    /** Convenience for tests: no extra sinks, own virtual-thread executor. */
    public ReceiptService(FxClient fx, DynamoDbTable<ReceiptItem> table, S3Client s3, SqsClient sqs,
                          AuditLogRepository audit, AwsProps props, ObjectMapper json, MeterRegistry registry) {
        this(fx, table, s3, sqs, audit, props, json, registry, List.of(), Executors.newVirtualThreadPerTaskExecutor());
    }

    /** extraSinks: optional cloud targets (e.g. GCP Pub/Sub + GCS under profile "gcp"); empty by default = AWS only. */
    @org.springframework.beans.factory.annotation.Autowired
    public ReceiptService(FxClient fx, DynamoDbTable<ReceiptItem> table, S3Client s3, SqsClient sqs,
                          AuditLogRepository audit, AwsProps props, ObjectMapper json, MeterRegistry registry,
                          List<ReceiptEventSink> extraSinks,
                          @org.springframework.beans.factory.annotation.Qualifier("receiptIoExecutor") ExecutorService io) {
        this.extraSinks = List.copyOf(extraSinks);
        this.io = io;
        this.fx = fx; this.table = table; this.s3 = s3; this.sqs = sqs; this.audit = audit; this.props = props; this.json = json;
        this.created = Counter.builder("receipts_issued").description("Receipts issued (not \"receipts_created\": Prometheus strips the OpenMetrics-reserved _created suffix)").register(registry);
    }

    /** Pure conversion step (FX lookup) - no side effects, so it's safe to fan out in parallel. */
    public Receipt convert(Receipt.Request req) {
        BigDecimal rate = "USD".equalsIgnoreCase(req.currency()) ? BigDecimal.ONE : fx.rateToUsd(req.currency());
        return new Receipt(UUID.randomUUID().toString(), req.payer(), req.amount(), req.currency(),
                req.amount().multiply(rate).setScale(2, RoundingMode.HALF_UP), Instant.now());
    }

    /**
     * Writes the receipt to DynamoDB, S3 and Mongo <b>in parallel</b> (they don't depend on each other), waits for all
     * three, and only then publishes the ReceiptCreated event, so a consumer never sees an event for a receipt that
     * isn't stored. Latency becomes max(writes) instead of sum(writes).
     *
     * <p>Not a distributed transaction: if one write fails, the others may already have succeeded (e.g. S3 object
     * written, DynamoDB put failed). The request then fails with {@link ReceiptWriteException} naming the failed
     * store(s), no event is sent, and the leftovers are orphans keyed by a receipt id nobody was given. A client retry
     * creates a new receipt id. See README "Receipt writes: parallel, not atomic".
     */
    public Receipt create(Receipt.Request req) {
        Receipt r = convert(req);
        String body = toJson(r);
        Map<String, CompletableFuture<?>> writes = new LinkedHashMap<>();
        writes.put("dynamodb", CompletableFuture.runAsync(() -> table.putItem(ReceiptItem.from(r)), io));
        writes.put("s3", CompletableFuture.runAsync(() -> s3.putObject(
                b -> b.bucket(props.bucket()).key("receipts/" + r.receiptId() + ".json"), RequestBody.fromString(body)), io));
        writes.put("mongo", CompletableFuture.runAsync(() -> audit.save(
                new AuditLog(null, "RECEIPT_CREATED", r.receiptId(), r.payer(), Instant.now())), io));
        awaitAll(r.receiptId(), writes);

        sqs.sendMessage(b -> b.queueUrl(queueUrl()).messageBody(toJson(                                 // SQS, after the writes
                new ReceiptCreated("ReceiptCreated", r.receiptId(), r.usdAmount()))));
        extraSinks.forEach(sink -> sink.onReceiptCreated(r, body));   // e.g. GCP Pub/Sub + Cloud Storage
        created.increment();
        log.info("receipt created id={} usd={}", r.receiptId(), r.usdAmount());
        return r;
    }

    /** Waits for every write (not fail-fast, so we can report all failures); throws if any failed. */
    private static void awaitAll(String receiptId, Map<String, CompletableFuture<?>> writes) {
        try {
            CompletableFuture.allOf(writes.values().toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException ignored) {
            // inspected per future below
        }
        Map<String, Throwable> failed = new LinkedHashMap<>();
        writes.forEach((store, f) -> {
            if (f.isCompletedExceptionally()) {
                try { f.join(); } catch (CompletionException e) { failed.put(store, e.getCause() != null ? e.getCause() : e); }
                catch (java.util.concurrent.CancellationException e) { failed.put(store, e); }
            }
        });
        if (!failed.isEmpty()) {
            failed.forEach((store, e) -> log.error("receipt write failed id={} store={}: {}", receiptId, store, e.toString()));
            throw new ReceiptWriteException(receiptId, failed);
        }
    }

    /** One or more of the parallel receipt writes failed; no event was published. */
    public static class ReceiptWriteException extends RuntimeException {
        private final Map<String, Throwable> failures;

        public ReceiptWriteException(String receiptId, Map<String, Throwable> failures) {
            super("receipt " + receiptId + " not created: write failed on " + String.join(", ", failures.keySet()),
                    failures.values().iterator().next());
            this.failures = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(failures)); // keeps store order
            failures.values().stream().skip(1).forEach(this::addSuppressed);
        }

        public java.util.Set<String> failedStores() { return failures.keySet(); }
    }

    /** Fan-out FX conversions: one virtual thread per item, then join. I/O-bound work -> virtual threads fit. */
    public List<Receipt> convertBatch(List<Receipt.Request> reqs) {
        List<CompletableFuture<Receipt>> futures = reqs.stream()
                .map(r -> CompletableFuture.supplyAsync(() -> convert(r), io)).toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    public Receipt get(String id) {
        ReceiptItem item = table.getItem(Key.builder().partitionValue(id).build());
        return item == null ? null : item.toReceipt();
    }

    /** Correct the payer name on a stored receipt (DynamoDB is the source of truth). Returns null if unknown. */
    public Receipt updatePayer(String id, String payer) {
        ReceiptItem item = table.getItem(Key.builder().partitionValue(id).build());
        if (item == null) return null;
        item.setPayer(payer);
        table.putItem(item);
        return item.toReceipt();
    }

    /** Small scan for read-only consumers (python-svc receipt-insights). Fine for a lab table; a big table would need a GSI/pagination. */
    public List<Receipt> list(int limit) {
        return table.scan(b -> b.limit(Math.min(limit, 500))).items().stream().limit(limit).map(ReceiptItem::toReceipt).toList();
    }

    String queueUrl() {
        if (queueUrl == null) queueUrl = sqs.getQueueUrl(b -> b.queueName(props.queue())).queueUrl();
        return queueUrl;
    }

    private String toJson(Object o) {
        try { return json.writeValueAsString(o); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    public record ReceiptCreated(String type, String receiptId, BigDecimal usdAmount) {}
}
