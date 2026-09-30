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

## Integration test
`ReceiptFlowIT` starts LocalStack (S3, SQS, DynamoDB) and MongoDB via Testcontainers, stubs FX with WireMock, and checks the whole POST flow end to end, including the poller consuming the event and the Prometheus output.
