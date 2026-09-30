package com.deepak.misclab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
    private volatile String queueUrl;

    public ReceiptService(FxClient fx, DynamoDbTable<ReceiptItem> table, S3Client s3, SqsClient sqs,
                          AuditLogRepository audit, AwsProps props, ObjectMapper json, MeterRegistry registry) {
        this(fx, table, s3, sqs, audit, props, json, registry, List.of());
    }

    /** extraSinks: optional cloud targets (e.g. GCP Pub/Sub + GCS under profile "gcp"); empty by default = AWS only. */
    @org.springframework.beans.factory.annotation.Autowired
    public ReceiptService(FxClient fx, DynamoDbTable<ReceiptItem> table, S3Client s3, SqsClient sqs,
                          AuditLogRepository audit, AwsProps props, ObjectMapper json, MeterRegistry registry,
                          List<ReceiptEventSink> extraSinks) {
        this.extraSinks = List.copyOf(extraSinks);
        this.fx = fx; this.table = table; this.s3 = s3; this.sqs = sqs; this.audit = audit; this.props = props; this.json = json;
        this.created = Counter.builder("receipts_issued").description("Receipts issued (not \"receipts_created\": Prometheus strips the OpenMetrics-reserved _created suffix)").register(registry);
    }

    /** Pure conversion step (FX lookup) - no side effects, so it's safe to fan out in parallel. */
    public Receipt convert(Receipt.Request req) {
        BigDecimal rate = "USD".equalsIgnoreCase(req.currency()) ? BigDecimal.ONE : fx.rateToUsd(req.currency());
        return new Receipt(UUID.randomUUID().toString(), req.payer(), req.amount(), req.currency(),
                req.amount().multiply(rate).setScale(2, RoundingMode.HALF_UP), Instant.now());
    }

    public Receipt create(Receipt.Request req) {
        Receipt r = convert(req);
        table.putItem(ReceiptItem.from(r));                                        // DynamoDB
        s3.putObject(b -> b.bucket(props.bucket()).key("receipts/" + r.receiptId() + ".json"),
                RequestBody.fromString(toJson(r)));                                // S3
        sqs.sendMessage(b -> b.queueUrl(queueUrl()).messageBody(toJson(               // SQS
                new ReceiptCreated("ReceiptCreated", r.receiptId(), r.usdAmount()))));
        audit.save(new AuditLog(null, "RECEIPT_CREATED", r.receiptId(), r.payer(), Instant.now())); // MongoDB
        extraSinks.forEach(sink -> sink.onReceiptCreated(r, toJson(r)));   // e.g. GCP Pub/Sub + Cloud Storage
        created.increment();
        log.info("receipt created id={} usd={}", r.receiptId(), r.usdAmount());
        return r;
    }

    /** Fan-out FX conversions: one virtual thread per item, then join. I/O-bound work -> virtual threads fit. */
    public List<Receipt> convertBatch(List<Receipt.Request> reqs) {
        try (ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Receipt>> futures = reqs.stream()
                    .map(r -> CompletableFuture.supplyAsync(() -> convert(r), vt)).toList();
            return futures.stream().map(CompletableFuture::join).toList();
        }
    }

    public Receipt get(String id) {
        ReceiptItem item = table.getItem(Key.builder().partitionValue(id).build());
        return item == null ? null : item.toReceipt();
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
