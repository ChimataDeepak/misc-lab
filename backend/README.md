# misc-lab backend (receipt service)

`POST /api/receipts {payer, amount, currency}` -> FX to USD -> DynamoDB + S3 + SQS event + Mongo audit.
Run unit tests: `./mvnw test` (no Docker). Integration tests: `./mvnw verify` (Testcontainers, needs Docker / Rancher Desktop).

## 1. AWS SDK v2 (S3, SQS)
- **Built:** `AwsConfig` builds `S3Client`/`SqsClient` against LocalStack; `ReceiptService.create` does `putObject` of the receipt JSON and `sendMessage` of a `ReceiptCreated` event; `ReceiptEventPoller` long-polls, logs, counts (`receipt_events_consumed_total`) and deletes.
- **Why:** S3 + SQS are the default storage/async building blocks on AWS; a queue decouples producers from consumers.
- **Interview:** "Delete is the ack in SQS: if my consumer crashes before deleting, the message reappears after the visibility timeout, so consumers must be idempotent. I use LocalStack locally with an endpoint override; in AWS the default credential chain replaces static keys."

## 2. DynamoDB (enhanced client)
- **Built:** `ReceiptItem` is a `@DynamoDbBean` with `receiptId` partition key; `table.putItem` / `table.getItem` via `DynamoDbEnhancedClient`.
- **Why:** key-value NoSQL with single-digit-ms reads at any scale; the access pattern (get by id) drives the key design.
- **Interview:** "In DynamoDB you design keys from queries first. The enhanced client maps a bean to items like a lightweight ORM; for other access patterns I'd add a GSI rather than scan."

## 3. MongoDB (Spring Data)
- **Built:** `AuditLog` document in `audit_log`, saved through a `MongoRepository` on every create; `findByReceiptId` is a derived query.
- **Why:** document stores suit flexible, append-heavy data like audit trails.
- **Interview:** "Mongo stores JSON-like documents without a fixed schema, so audit events can evolve freely. Spring Data derives queries from method names, same model as JPA."

## 4. Resilient third-party client (RestClient + Resilience4j)
- **Built:** `FxClient` uses `RestClient` with connect/read timeouts, `@Retry(fallbackMethod)` outside `@CircuitBreaker`. Fallback returns `fx.fallback-rate`. `FxClientTest` (WireMock) proves: success, retry-then-fallback on 500, timeout fallback, circuit OPEN stops calling the provider.
- **Why:** a slow or failing dependency must not take your service down with it.
- **Interview:** "Timeout bounds each call, retry handles transient blips, and the breaker stops hammering a dead provider and fails fast. I put the fallback on the outer retry and ignore CallNotPermittedException in retry, so an open breaker goes straight to fallback."

## 5. Concurrency (CompletableFuture + virtual threads)
- **Built:** `POST /api/receipts/batch` -> `convertBatch` fans out one `CompletableFuture` per item on `Executors.newVirtualThreadPerTaskExecutor()` and joins in order. `ReceiptServiceTest.batchRunsConversionsInParallel` uses a `CountDownLatch` that only opens when all 20 calls are in flight, so sequential code would fail.
- **Why:** FX lookups are I/O-bound; parallelising them cuts latency from sum to max.
- **Interview:** "Virtual threads make blocking I/O cheap, one per task, no pool sizing. The latch test proves concurrency deterministically instead of relying on timing."

## 6. Observability (Actuator, Micrometer, JSON logs)
- **Built:** `/actuator/prometheus` exposes `receipts_issued_total` (counter), `fx_call_seconds` (timer), `receipt_events_consumed_total` and circuit-breaker state; `logging.structured.format.console=ecs` gives JSON logs.
- **Why:** you can't operate what you can't see; JSON logs are machine-searchable.
- **Interview:** "Counters for business events, timers for dependency latency, both scraped by Prometheus. Structured ECS logs mean Kibana/Datadog can filter by field instead of regexing text."

## 7. Azure Blob Storage (SDK + Azurite)
- **Built:** `AzureBlobReceiptArchive` uploads receipt JSON with the Azure Storage Blob SDK. Local emulator is Azurite (`docker compose` service `azurite`, Blob port 10000). `misclab.azure.enabled=true` wires a Spring bean; default is off so LocalStack ITs do not need Azure. `AzureBlobReceiptArchiveIT` is the Docker proof; `AzureBlobReceiptArchiveTest` mocks the container client.
- **Why:** Target JDs (Adobe, Citi, Cohere) list Azure among cloud skills; this is Blob only, not AKS/App Service.
- **Interview:** "Same pattern as S3: object key `receipts/{id}.json`, overwrite allowed. UseDevelopmentStorage or a real connection string. On Rancher Desktop, `TESTCONTAINERS_RYUK_DISABLED=true` is needed for the Azurite IT."

## Integration test
`ReceiptFlowIT` starts LocalStack (S3, SQS, DynamoDB) and MongoDB via Testcontainers, stubs FX with WireMock, and checks the whole POST flow end to end, including the poller consuming the event and the Prometheus output.

## Payments: PaymentIntent lifecycle, idempotency, signed webhooks (`payments/`)

**What:** `POST /api/payments` (Idempotency-Key required) creates a payment for a receipt. Then `/{id}/authorize`,
`/{id}/capture` (Idempotency-Key), `/{id}/refund {amountMinor}` (Idempotency-Key, partial or full) and `/{id}/void`.
- **State machine** (`PaymentStatus.next()`): CREATED→AUTHORIZED→CAPTURED→PARTIALLY_REFUNDED*→REFUNDED. VOIDED is reachable only from CREATED or AUTHORIZED. Anything else is `409` (`illegal transition CAPTURED -> VOIDED`).
- **Money** (`Money`) is `long` minor units plus an ISO-4217 code. Mixed currencies throw, refunds can't exceed what was captured, and `Math.addExact` rules out silent overflow.
- **Idempotency** (`IdempotencyStore`): same key and same body returns the stored result (no second capture or refund, no second webhook). Same key with a different body is `422`, and a missing key is `400`. `ConcurrentHashMap.computeIfAbsent` means 50 concurrent retries create exactly one payment (tested).
- **Webhooks** (`WebhookSigner`, `WebhookPublisher`): every transition POSTs a JSON event to `misclab.payments.webhook-url` with `Misclab-Signature: t=<unix>,v1=<HMAC-SHA256("t.body")>` and `Misclab-Event-Id`. **Verifier** (`WebhookVerifier`, also `POST /api/payments/webhooks/receive`): constant-time compare, 5-minute timestamp window, and event-id replay cache.

**Tests:** `PaymentIntentTest` (13: the full 6×6 transition table plus every lifecycle path and money rule), `PaymentServiceTest` (6: idempotent retries, key reuse, 50-thread create), `WebhookSignatureTest` (8: known vector shared with go-verify, tamper, wrong secret, moved or stale timestamp, replay, malformed), and `PaymentApiIntegrationTest` (the whole lifecycle through Spring MVC, with a WireMock "merchant" receiving 4 webhooks that each verify).

**Honest limits:** everything is in memory (payments, idempotency keys, outbox). A real system would use a DB table with a unique key on (scope, idempotency key), a transactional outbox, and retried delivery with backoff. There is one full capture (no multi-capture) and no real card network or PSP.

**How I'd explain it in an interview:** "Payments are a state machine. I kept the allowed-transition table in one enum and
test all 36 pairs. Amounts are integer minor units. Create, capture and refund take an Idempotency-Key, so a client retry after a timeout
can't double-charge or double-refund. A key reused with a different body is rejected, not silently replayed. Webhooks are
HMAC-signed over timestamp + body. The receiver checks the signature in constant time, rejects anything outside 5 minutes,
and remembers event ids to stop replays. A Go verifier shares the same test vector."

## GCP: Pub/Sub + Cloud Storage (`gcp/`, profile `gcp`)

**What:** with `--spring.profiles.active=gcp`, each new receipt is also archived as JSON to Cloud Storage
(`misc-receipts-gcs/receipts/<id>.json`) and a `ReceiptCreated` message is published to Pub/Sub topic `receipt-created`, with
`type` and `receiptId` attributes. It uses the official `google-cloud-pubsub` / `google-cloud-storage` clients (libraries-bom 26.50.0).
Without the profile nothing GCP loads and the AWS path is unchanged. `ReceiptService` calls an optional list of
`ReceiptEventSink`s, and GCP is one of them.

**Local:** the Pub/Sub emulator (`gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators`) and `fsouza/fake-gcs-server`. Emulator mode uses
a plaintext gRPC channel and `NoCredentials`. Blank `pubsub-emulator-host`/`storage-endpoint` means real GCP with Application Default Credentials (not tried).

**Test:** `GcpReceiptFlowIT` (Testcontainers: LocalStack + Mongo + Pub/Sub emulator + fake-gcs + WireMock FX) POSTs a receipt,
reads the GCS object back, pulls the Pub/Sub message from a subscription, and checks its attributes and body.

**How I'd explain it in an interview:** "Same domain event, second cloud. S3 maps to Cloud Storage and SQS maps to Pub/Sub. The
difference that matters: Pub/Sub is topic-plus-subscription fan-out with at-least-once delivery, so consumers dedupe on receiptId. I
waited on the publish future so 'created' means 'Pub/Sub accepted it'. I tested against the official emulator, not a real project."
