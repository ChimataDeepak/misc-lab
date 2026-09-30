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

Verification status per skill (what a passing test actually exercises) is in RUN-RESULTS.md, section "30 Sep 2026, round 2"; Postgres payments and the parallel-write load test are in "round 3".

Proof it runs: **[RUN-RESULTS.md](RUN-RESULTS.md)** — every step, its command, pass/fail and numbers, plus the 10 real problems hit and fixed.

## AI-assisted development (honest note)
Built with Claude Code agents: three builder agents worked in parallel on disjoint folders (backend+lambda, infra+perf+observability+CI, frontend+e2e) against a shared contract (names, ports, metrics). The combined dry run and all the fixes in RUN-RESULTS.md were done step by step with the results checked by hand. Before claiming any skill in an interview, read that folder's README — each ends with "how to explain this in an interview".
