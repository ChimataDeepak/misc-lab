# misc-lab — hands-on for the skills my target JDs ask for

Branch `miscellaneous` of banking-backend is an **orphan branch**: no shared history or files with `main`. banking-backend is the main project; this is a deliberately small side project that gives real, tested hands-on use of each skill that job descriptions I'm applying to require and banking-backend doesn't cover.

**Domain:** a receipt service. `POST /api/receipts {payer, amount, currency}` → converts to USD via an external FX API → stores the receipt → emits an event.

| JD skill | Where | What it does |
|---|---|---|
| AWS S3, SQS, DynamoDB (SDK v2) | `backend/…/ReceiptService.java`, `AwsConfig.java`, `ReceiptEventPoller.java` | Receipt JSON to S3, item in DynamoDB, `ReceiptCreated` event on SQS, poller consumes it |
| AWS Lambda | `lambda/` | Java handler triggered by S3 `ObjectCreated` |
| Terraform (IaC) | `infra/terraform/` | Creates bucket, queue, table, IAM role, Lambda + S3 trigger on LocalStack; `use_localstack=false` for real AWS |
| MongoDB (NoSQL) | `AuditLog*.java` | Audit log per receipt (Spring Data Mongo) |
| Resilient REST client | `FxClient.java` | Spring `RestClient` + Resilience4j retry, circuit breaker, fallback, timeouts |
| Concurrency | `POST /api/receipts/batch` | Parallel FX conversion with `CompletableFuture` on virtual threads |
| Observability / SRE | `observability/`, Micrometer in backend | Prometheus metrics (counter, histogram), JSON logs, 99.5% SLO multi-window burn-rate alerts |
| React + TypeScript | `frontend/` | Form + list, custom hook, Vitest + Testing Library |
| Angular (standalone) | `frontend-angular/` | Tiny receipt-id lookup against the same REST API; Vitest on `ReceiptApi` + a TestBed (JIT, zoneless) component test that types an id, clicks Load and checks the rendered result |
| Selenium | `e2e/` | Page Object + Chromium in Testcontainers |
| JMeter | `perf/` | 20-user load test with assertions and HTML report |
| Testcontainers, CI | `backend/…/*IT.java`, `.github/workflows/ci.yml` | LocalStack + Mongo integration tests; CI for all parts |
| GraphQL | `backend/…/ReceiptGraphqlController.java`, `graphql/schema.graphqls` | `receipt(id)` query over the same service as REST |
| gRPC / protobuf | `backend/src/main/proto/receipts.proto`, `ReceiptGrpcService` | `GetReceipt` RPC; unit tests, no extra port in `mvnw test` |
| Azure Blob (Azurite) | `AzureBlobReceiptArchive.java` | Receipt JSON to Azure Blob via official SDK; emulator, not a real subscription |
| Elasticsearch | `ElasticsearchReceiptClient.java` | Index + payer search over the ES REST API; WireMock unit test **and** `ElasticsearchReceiptClientIT` against real Elasticsearch 8.15 in Testcontainers |
| Spark (local) | `spark/ReceiptPayerCount.java` | `local[1]` payer word-count on receipt lines; not Databricks/Hadoop |
| OAuth2/OIDC resource server | `OidcResourceServerConfig`, `/api/oidc/me` | JWT issuer+audience on one path; HMAC lab IdP, not Okta auth-code |
| LLM HTTP stub | `LlmClient.java` | OpenAI-compatible `/v1/chat/completions` against WireMock; no real key |
| PCF / Cloud Foundry | `manifest.yml` | Deploy descriptor only; no live PCF space (**not verified**) |
| **Payments** | `backend/…/payments/` | PaymentIntent state machine (CREATED→AUTHORIZED→CAPTURED→partial/full REFUND, VOID), `Idempotency-Key` on create/capture/refund, integer minor-unit money, HMAC-SHA256-signed webhooks with timestamp + replay protection and a verifier |
| **PostgreSQL / JPA / Flyway** | `payments/PaymentEntity.java`, `PaymentRepository.java`, `IdempotencyStore.java`, `db/migration/V1__payments.sql` | Payments, idempotency keys, webhook outbox and received-event ids in Postgres 16; row lock (`SELECT … FOR UPDATE`) + `@Version`; Idempotency-Key is a primary key; tests on Testcontainers Postgres |
| **Python / FastAPI** | `python-svc/` | `receipt-insights`: `GET /insights/summary` (totals by currency, top payers) from the backend's `GET /api/receipts`; CLI; pytest; Dockerfile |
| **GCP Pub/Sub + Cloud Storage** | `backend/…/gcp/`, profile `gcp` | `ReceiptCreated` to Pub/Sub, receipt JSON to GCS with the official Java clients; Testcontainers IT on the Pub/Sub emulator + fake-gcs-server |
| **Go** | `go-verify/` | CLI that verifies the payment webhook signatures; shares a test vector with the Java signer |
| **Redis caching** | `backend/…/cache/` | Spring Cache cache-aside on Redis for FX rates and receipt lookups; per-cache TTL (10 min / 1 h), JSON values, evict-on-update, `cache.gets{result=hit\|miss}` metrics; off unless `misclab.cache.enabled=true` |
| **SOAP (Spring Web Services)** | `backend/…/soap/`, `resources/xsd/receipts.xsd` | Contract-first `GetReceipt`: XSD → JAXB classes (build time) → WSDL at `/ws/receipts.wsdl`; unknown id = SOAP Client fault |
| **RabbitMQ (Spring AMQP)** | `backend/…/messaging/` | `ReceiptCreated` to topic exchange `receipts.events` (`receipt.created.<ccy>`), idempotent consumer, dead-letter exchange + DLQ for poison/contract-breaking messages |
| **Cassandra** | `backend/…/cassandra/`, `resources/cassandra/schema.cql` | `receipts_by_payer` time series: partition key payer, clustering `created_at DESC`; "latest N for payer" is one-partition read |
| **Oracle Database** | `resources/db/oracle/V1__payments.sql`, profile `oracle` | Same payments schema + JPA repository on Oracle 23ai Free (Testcontainers `gvenzl/oracle-free`, arm64) |
| **HTML / CSS (server-rendered)** | `templates/dashboard.html`, `static/css/dashboard.css` | Thymeleaf receipts dashboard at `/dashboard`, hand-written responsive CSS (grid cards, table → labelled blocks under 640 px, dark mode), escaped output |
| **TDD** | `payments/retry/` | Webhook retry: exponential backoff + full jitter, max-attempts → DEAD_LETTER; red commit `24500e5`, green commit `22f41b3` |
| **Jakarta EE APIs via Spring Boot** | `validation/IsoCurrency*.java`, `Receipt.Request` | Jakarta Bean Validation custom constraint + standard ones, enforced by `@Valid` (400 before the service runs). Not a full Jakarta EE app server |

## Payments on PostgreSQL (how it's safe under concurrency)

Schema is owned by Flyway (`backend/src/main/resources/db/migration/V1__payments.sql`); Hibernate only validates it (`ddl-auto: validate`). `docker compose up -d` starts `postgres:16` on host port **5433** (5432 is often a local Postgres). Tests get a throwaway Postgres 16 from Testcontainers via the `jdbc:tc:` URL in `backend/src/test/resources/application.properties`.

| Table | Purpose | Concurrency guarantee |
|---|---|---|
| `payment_intents` | state, amounts in minor units, `version` | transitions run `SELECT … FOR UPDATE` then `UPDATE … WHERE version=?` (`@Version`); CHECK constraints stop refunded > captured > amount even if code is wrong |
| `idempotency_keys` | `(scope, idem_key)` **primary key**, fingerprint, stored JSON response | `INSERT … ON CONFLICT DO NOTHING` claims the key; a concurrent duplicate **waits** on the winner's uncommitted row, then reads its stored response. If the winner rolls back (e.g. 409), the key is free again |
| `webhook_outbox` | every signed event, FK to the payment | inserted in the **same transaction** as the state change; HTTP delivery runs **after commit**, so a rolled-back change never notifies the merchant |
| `webhook_received_events` | event ids the receiver accepted | primary key on `event_id`: replay protection survives a restart and works across instances |

Each service call is one transaction (key claim + row change + outbox event). Tests: 50 concurrent same-key creates → 1 row + 1 event; 20 concurrent refunds of 100 on a 1000 capture → exactly 10 succeed, refunded = 1000; a failed capture leaves no key row; a second verifier instance rejects an already-accepted event id.
Not done (kept honest): no outbox relay/retry with backoff (delivery is best-effort once after commit), no TTL cleanup job for idempotency keys.

## Receipt writes: parallel, not atomic

`POST /api/receipts` converts via FX, then writes **DynamoDB, S3 and the Mongo audit log in parallel** (`CompletableFuture.runAsync` on the shared virtual-thread executor, `ExecutorConfig.receiptIoExecutor`), waits for all three, and only then publishes `ReceiptCreated` to SQS (and the optional GCP sinks). Latency of the write phase becomes max(writes) instead of their sum. **Measured effect: small.** A single receipt POST takes ~35 ms end to end, so parallel writes save a few ms; the ~1 s p95 under 20 users is queueing, and the JMeter re-run showed no reliable gain (RUN-RESULTS round 3).

Error handling: all three writes are awaited (not fail-fast) so every failed store is reported. Any failure → `ReceiptWriteException` → **HTTP 503** with `failedStores` in the problem detail, no event, metric not incremented.

Consistency trade-off: this is **not a distributed transaction**. If S3 fails after DynamoDB succeeded, the DynamoDB item (and maybe the audit doc) stay behind as orphans under a receipt id the client never received; a client retry creates a new receipt id. That was already true with sequential writes (a later write failing left earlier ones behind); parallelism only changes which subset can be left. What keeps it acceptable here: consumers only learn about a receipt through the SQS event, which is sent only after all writes succeed. Production options: a cleanup/reconciliation job keyed by receipt id, a client-supplied idempotency key for receipts, or write one source of truth (DynamoDB) and fan out to S3/Mongo from its stream (DynamoDB Streams → outbox pattern).

## Round 4 gap-fill: how I'd explain each one in an interview

All of these are small and sit on the same receipt/payment domain. Optional infrastructure (Redis, RabbitMQ, Cassandra) is **off by default** (`misclab.*.enabled=false`) so the app and the older tests still start without it; each integration test turns its feature on and gets a real broker/database from Testcontainers.

**Redis cache-aside.** "FX rates come from a slow, rate-limited provider and receipt reads hit DynamoDB, so I put Spring Cache in front of both, backed by Redis. On a miss the method runs and the result is stored with a per-cache TTL (FX 10 minutes because rates move, receipts 1 hour). Values are JSON with a typed serializer, not Java serialization. On an update I write the source of truth first and then **evict** rather than put, so two racing writers can't leave a stale value; the next read reloads. Nulls aren't cached, so a receipt created later is still found. I turned on cache statistics so Micrometer exports `cache.gets` by hit/miss, which is how you'd tune TTLs. The test proves the second lookup never calls the source and that eviction forces a reload." Trade-off I'd mention: cache-aside can still serve data up to TTL old if a write bypasses the service; that's why TTLs stay short.

**SOAP, contract first.** "The XSD is the contract. The build generates JAXB classes from it and Spring-WS builds the WSDL from the same XSD, so code and contract can't drift. The endpoint is a thin adapter over the same `ReceiptService` that REST, GraphQL and gRPC use. An unknown id is a SOAP *Client* fault, the SOAP version of a 4xx. I tested through the real message dispatcher with `MockWebServiceClient`, including validating the response against the XSD." Why it matters: banks and ERPs still integrate over SOAP.

**RabbitMQ with a dead-letter queue.** "After a receipt is stored, it's published to a topic exchange with routing key `receipt.created.<currency>`, so consumers can bind to all or one currency. The consumer is idempotent because RabbitMQ is at-least-once. A message that can't be parsed, or parses but breaks the contract (e.g. negative amount), is rejected **without requeue**, and the queue's dead-letter exchange routes it to a DLQ instead of looping forever and blocking the queue. The broker adds an `x-death` header saying why. The integration test on a real RabbitMQ covers the happy path, a duplicate, bad JSON and a contract violation." Next step I'd name: retry with delay (TTL queue or delayed exchange) before dead-lettering transient failures.

**Cassandra time series.** "In Cassandra you design the table for the query. The query is 'latest N receipts for a payer', so payer is the partition key and `created_at DESC` is the clustering key: rows are stored newest first, and `LIMIT n` reads one partition with no sort. `receipt_id` is a last clustering column so two receipts in the same millisecond don't overwrite each other, and writes are upserts, so a redelivered event is harmless. For a payer with millions of rows I'd add a time bucket (e.g. month) to the partition key to keep partitions bounded."

**Oracle portability.** "The payments schema was written for PostgreSQL. I added an Oracle version of the Flyway migration under a profile and ran the same JPA entity and repository against Oracle 23ai Free in Testcontainers. Hibernate's schema validation passed, `SELECT … FOR UPDATE` and `@Version` work the same, CHECK constraints still reject an over-refund." Differences I had to handle: `BIGINT`→`NUMBER(19)`, `VARCHAR`→`VARCHAR2`, `TEXT`→`CLOB` (a 10 KB webhook body exceeds `VARCHAR2`'s 4000 bytes), `TIMESTAMPTZ`→`TIMESTAMP(6) WITH TIME ZONE` (microseconds preserved, tested), `now()`→`SYSTIMESTAMP`, `BIGSERIAL`→identity column, `DEFAULT` must come before `NOT NULL`, constraints named so errors are readable, and Flyway's history table is a quoted lower-case identifier on Oracle. **Not ported:** `IdempotencyStore` uses PostgreSQL's `INSERT … ON CONFLICT DO NOTHING`; Oracle would need `MERGE` or catching `ORA-00001`, so the idempotency/concurrency tests still run on Postgres only.

**HTML/CSS dashboard.** "A server-rendered page with Thymeleaf: the controller builds the model (rows newest first, USD totals per currency) and the template renders it, so the page works without JavaScript. CSS is hand-written: custom properties for theming and dark mode, a CSS grid of summary cards that wraps by itself, and under 640 px the table turns into labelled blocks using `data-label` + `::before`. `th:text` escapes output, so a payer named `<script>` shows as text; the MockMvc test checks rows, order, totals and escaping."

**TDD: webhook retry.** "I wrote the tests first and committed them failing against empty stubs (commit `24500e5`: 10 tests, 1 failure + 9 errors), then implemented until green (commit `22f41b3`: 10/10). The policy is exponential backoff with **full jitter**: wait random(0, min(cap, base·2^(n−1))). Jitter spreads retries so many failed webhooks don't hit the merchant in the same second. The random source and the clock are injected, so tests are deterministic. The state machine is immutable: PENDING → DELIVERED, or after `maxAttempts` failures → DEAD_LETTER, which is terminal. The tests forced two details I might have missed: the shift can overflow for large attempt numbers (capped before shifting), and an exception from the sender must count as a failed attempt, not crash the relay." Honest scope: the policy and state machine are done and tested; wiring them into a scheduled outbox relay with a `delivery_status` column is the next step.

**Jakarta EE APIs via Spring Boot.** "Spring Boot 3 is built on Jakarta EE 10 APIs, so I use several of them directly: `jakarta.persistence` (JPA entities, `@Version`, pessimistic locks in payments), `jakarta.validation` (Bean Validation), and `jakarta.servlet` underneath Spring MVC on embedded Tomcat. I wrote a custom constraint, `@IsoCurrency`, with a `ConstraintValidator` that accepts only real ISO-4217 codes, and put it with `@NotBlank`/`@Positive` on the receipt request; `@Valid` makes Spring return 400 before the service runs." I'd be clear this is **Jakarta EE APIs inside Spring Boot**, not an app on WildFly/Payara/WebLogic, no EJB/CDI container.

Verification status per skill (what a passing test actually exercises) is in RUN-RESULTS.md, section "30 Sep 2026, round 2"; Postgres payments and the parallel-write load test are in "round 3".

Proof it runs: **[RUN-RESULTS.md](RUN-RESULTS.md)** — every step, its command, pass/fail and numbers, plus the 10 real problems hit and fixed.

## AI-assisted development (honest note)
Built with Claude Code agents: three builder agents worked in parallel on disjoint folders (backend+lambda, infra+perf+observability+CI, frontend+e2e) against a shared contract (names, ports, metrics). The combined dry run and all the fixes in RUN-RESULTS.md were done step by step with the results checked by hand. Before claiming any skill in an interview, read that folder's README — each ends with "how to explain this in an interview".
