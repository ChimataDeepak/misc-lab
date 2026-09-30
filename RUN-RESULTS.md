# Combined dry run — 30 Sep 2026

One run of everything together on Rancher Desktop (Docker 29.1, macOS arm64). All numbers are from this run.

| # | Step | Command | Result |
|---|---|---|---|
| 1 | Local AWS + deps up | `docker compose up -d` (LocalStack 3.8, Mongo 7, WireMock, Prometheus) | PASS |
| 2 | Lambda jar | `cd lambda && ./mvnw package` | PASS — shaded jar 1.16 MB |
| 3 | Infra as code | `./infra/tf.sh init && ./infra/tf.sh apply -auto-approve` | PASS — 8 resources (S3 bucket, SQS queue, DynamoDB table, IAM role, Lambda, permission, S3→Lambda notification) |
| 4 | Backend tests | `cd backend && ./mvnw verify` | PASS — 6 unit + 1 Testcontainers IT (LocalStack S3/SQS/DynamoDB + Mongo + WireMock) |
| 5 | Live end-to-end | `java -jar backend/target/*.jar`, then `curl POST /api/receipts` (1000 INR) | PASS — 12.00 USD; S3 object, DynamoDB item, Mongo audit doc all present; SQS event consumed (JSON log) |
| 6 | Lambda trigger | upload via the app → check `/aws/lambda/misc-receipt-on-upload` logs | PASS — `processed 1 object(s), 164 bytes`, 502 ms |
| 7 | Circuit breaker | `docker compose stop wiremock`, 6× POST | PASS — fallback rate used (11.90 USD), breaker state `OPEN`; recovered after restart |
| 8 | Metrics | `GET /actuator/prometheus` | PASS — `receipts_issued_total`, `receipt_events_consumed_total`, `fx_call_seconds_*` histogram |
| 9 | SLO alerts | Prometheus `/api/v1/rules`, `/api/v1/targets` | PASS — 3 alerts + 4 recording rules loaded, scrape target `up` |
| 10 | React | `cd frontend && npm test -- --run && npm run build` | PASS — 4 tests, build 545 ms |
| 11 | Selenium | Vite dev server + `cd e2e && mvn verify` | PASS — Chromium in Testcontainers creates a receipt through the UI |
| 12 | Load test | `./perf/run.sh` (JMeter, 20 users × 10 loops) | **Functional PASS, latency SLA FAIL** — 200 req, 0 HTTP errors, ~18 req/s, mean 727 ms, p95 1074 ms; 25/200 (12.5%) broke the 1 s assertion |

## What broke on the way (and the fix) — good interview material

1. **Testcontainers couldn't find Docker** — Rancher Desktop's socket is `~/.rd/docker.sock`. Fix: `DOCKER_HOST=unix://$HOME/.rd/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.
2. **`client version 1.32 is too old`** — Docker 29 rejects API < 1.41. Fix: `src/test/resources/docker-java.properties` → `api.version=1.44`.
3. **`/actuator/prometheus` 404 in tests** — `@SpringBootTest` disables metrics export by default. Fix: `@AutoConfigureObservability`.
4. **`receipts_created_total` missing** — the Prometheus Java client strips the `_created` suffix (reserved by OpenMetrics for counter creation timestamps), so it became `receipts_total`. Fix: renamed to `receipts_issued`.
5. **Prometheus / JMeter couldn't reach the app** — `host-gateway` points at the Rancher VM, not the Mac. Fix: rely on `host.docker.internal` (Docker/Rancher Desktop resolve it to the Mac).
6. **LocalStack restart wiped resources** — it's ephemeral; re-run `terraform apply` from fresh state.
7. **Selenium `SessionNotCreated`** — `standalone-chrome` is amd64-only; on Apple silicon use multi-arch `selenium/standalone-chromium`.
8. **`ERR_NAME_NOT_RESOLVED`** — `exposeHostPorts` was in `@BeforeAll`, which runs *after* `@Container` fields start. Fix: static initializer.
9. **Vite "Blocked request"** — DNS-rebinding protection. Fix: `allowedHosts: ['host.testcontainers.internal']`.
10. **HTTP 403 from the UI in Selenium** — the proxy forwarded `Origin: host.testcontainers.internal`, not in the backend's CORS list. Fix: proxy strips `Origin` (proxied calls are same-origin; CORS doesn't apply).

## Known limitation (kept, not hidden)
p95 is ~1.07 s because each receipt does S3 → DynamoDB → Mongo → SQS **sequentially** against emulated services, plus the FX call. Next step if wanted: run the three independent writes in parallel with `CompletableFuture` (the batch endpoint already shows the pattern), then re-measure.

## How to re-run
```bash
export DOCKER_HOST=unix://$HOME/.rd/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
export JAVA_HOME=$HOME/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home
docker compose up -d && (cd lambda && ./mvnw -q package) && ./infra/tf.sh init && ./infra/tf.sh apply -auto-approve
(cd backend && ./mvnw verify)
```

---

# 30 Sep 2026, round 2: payments, Python, GCP, Go + verification of the Cursor additions

Same machine (Rancher Desktop, macOS arm64, Corretto 21). Env as in "How to re-run" above.

| # | Step | Command | Result |
|---|---|---|---|
| 13 | Backend, full | `cd backend && ./mvnw verify` | PASS: **43 unit** (surefire) + **4 IT** (failsafe: `ReceiptFlowIT`, `GcpReceiptFlowIT`, `AzureBlobReceiptArchiveIT`, `ElasticsearchReceiptClientIT`), ~94 s. Run 3× in a row green after fix 12 below |
| 14 | Payments | included in 13 | PASS: `PaymentIntentTest` 13, `PaymentServiceTest` 6, `WebhookSignatureTest` 8, `PaymentApiIntegrationTest` 1 (full lifecycle via MVC; WireMock merchant got 4 webhooks, all verified; replay → 401) |
| 15 | GCP | `GcpReceiptFlowIT` | PASS: POST receipt → GCS object read back from fake-gcs-server, Pub/Sub message pulled from the emulator with `type`/`receiptId` attributes |
| 16 | Elasticsearch (real) | `ElasticsearchReceiptClientIT` | PASS against `elasticsearch:8.15.3` (was only WireMock before; see fix 13) |
| 17 | Python | `cd python-svc && .venv/bin/pytest -q` | PASS: 7 tests |
| 18 | Python live | backend jar + LocalStack/Mongo/WireMock, 3× POST, `receipt-insights --url http://localhost:8090 --top 2` | PASS: count 3, totalUsd 62.24, top payers ben 50 / asha 12.24 |
| 19 | Go | `docker run --rm -v "$PWD":/src -w /src golang:1.23-alpine go test -v ./...` in `go-verify/` | PASS: 2 test funcs, 9 cases (incl. the vector shared with Java). Go isn't installed on the Mac and there's no Homebrew, so it ran in the official Go image |
| 20 | Spark | `cd spark && ../backend/mvnw test` | PASS: 1 test (`local[1]` payer count) |
| 21 | React | `cd frontend && npm test -- --run && npm run build` | PASS: 4 tests, build 585 ms |
| 22 | Angular | `cd frontend-angular && npm test` | PASS: 3 tests (2 `ReceiptApi` + 1 new TestBed component test), after fix 11 |

## Verification of what Cursor added earlier

| Skill | Status | Evidence |
|---|---|---|
| GraphQL | VERIFIED | `ReceiptGraphqlControllerTest` (2, GraphQlTester over the schema). Files were untracked and are now committed |
| gRPC / protobuf | VERIFIED | `ReceiptGrpcServiceTest` (2); protoc codegen runs in the build. Files were untracked and are now committed |
| Angular | VERIFIED after fix 11 | new `app.component.spec.ts` renders the component, types an id, clicks Load and asserts the output |
| Azure Blob | VERIFIED (component) | `AzureBlobReceiptArchiveIT` against Azurite 3.33 + unit test. Note: the archive is **not wired into** `POST /api/receipts`; it's a standalone component |
| Elasticsearch | VERIFIED after fix 13 | new IT against real ES 8.15.3 |
| Spark | VERIFIED | `ReceiptPayerCountTest` on `local[1]` |
| OIDC resource server | VERIFIED | `OidcReceiptControllerTest` (401 without a JWT; claims echoed with an HS256 JWT). HMAC key, not a JWKS IdP |
| LLM client | VERIFIED (stub only) | `LlmClientTest` against a WireMock OpenAI-compatible endpoint; no real model called |
| PCF manifest | NOT VERIFIED | no `cf` CLI and no Cloud Foundry space; only checked that the jar path in `manifest.yml` matches the built jar |

## What broke this round (and the fix)

11. **Angular component could never run**: `constructor(private api: ReceiptApi)` needs decorator metadata that only the Angular compiler emits. The lab has no Angular CLI, so the new component test failed with `NG0202: This constructor is not compatible with Angular Dependency Injection`. Fix: `private readonly api = inject(ReceiptApi)`. Test setup: `@angular/compiler` (JIT) + zoneless change detection in Vitest/jsdom.
12. **Flaky `ReceiptFlowIT` (~1 in 3)**: `Connection refused` on LocalStack's mapped port during Spring startup. Testcontainers saw LocalStack's "Ready" log inside the container before Rancher Desktop had forwarded the port to the Mac. Fix: `HostPortReady.awaitHttp(...)` polls `/_localstack/health` from the host before handing the endpoint to Spring (also used for fake-gcs and ES). 3 consecutive full runs green after it.
13. **Elasticsearch client didn't work against real ES**: the WireMock test passed, but against ES 8.15 the search right after indexing returned nothing (near-real-time refresh). The query was also built by string concatenation (a backslash in a payer name broke the JSON), and `IOException` wrongly set the thread's interrupt flag. Fix: `?refresh=wait_for` on index, query built with Jackson, and separate catch blocks. Proven by the new IT, which fails on the old code.
14. **New read endpoint**: `GET /api/receipts?limit=` (DynamoDB scan, capped at 500) added for the Python service; asserted in `ReceiptFlowIT`.

## Still open / honest limits
- ~~Payments state, idempotency keys and the webhook outbox are in memory.~~ Done in round 3 (Postgres). Still open: outbox relay with retry/backoff.
- GCP ran against emulators only. The real-GCP path (ADC credentials) is written but not run.
- Go ran in Docker, not a local toolchain.
- p95 latency finding from round 1: re-measured in round 3 (the sequential-writes diagnosis turned out to be wrong).

---

# 30 Sep 2026, round 3: payments on PostgreSQL + parallel receipt writes

Same machine (Rancher Desktop, macOS arm64, 11 cores, Corretto 21). Note: the Rancher VM was also running an unrelated k8s stack (OpenSearch, Kafka, two app pods in a restart loop) the whole time, which made the load numbers below noisy.

| # | Step | Command | Result |
|---|---|---|---|
| 23 | Backend, full | `cd backend && ./mvnw verify` | PASS: **50 unit** (surefire, was 43) + **4 IT** (failsafe), 2 min 07 s. Payments tests now run against a real Postgres 16 (Testcontainers `jdbc:tc:` URL). See fix 15 for a first run that failed |
| 24 | Payments on Postgres | `PaymentServiceTest` (10, was 6), `PaymentApiIntegrationTest` (1) | PASS: 50 concurrent same-key creates → 1 row in `payment_intents` + 1 outbox event; 20 concurrent refunds of 100 on a 1000 capture → exactly 10 succeed, refunded = 1000, status REFUNDED; failed capture (409) leaves no idempotency row and the key works after authorize; an event id accepted by one verifier is rejected by a second instance (replay check in the DB); full MVC lifecycle + merchant webhooks as before |
| 25 | Parallel receipt writes | `ReceiptServiceTest` (5, was 2) | PASS: latch test proves DynamoDB/S3/Mongo writes run concurrently; SQS send happens after them; an S3 failure → `ReceiptWriteException` naming `s3`, no SQS event, counter not incremented; two failures are both reported |
| 26 | Live restart check | jar + `docker compose up -d` (now incl. `postgres:16` on 5433), create + authorize a payment, restart the JVM | PASS: `GET` after restart returns AUTHORIZED; same Idempotency-Key after restart returns the same payment id; outbox still has its 2 events |
| 27 | Load test before/after | `./perf/run.sh` (20 users × 10 loops), old jar and new jar alternated, 5 runs each, after `terraform apply` on LocalStack and 10 warm-up requests | see below: **no reliable improvement** |
| 28 | Single-client latency | 100 sequential `curl` POSTs per jar, 2 rounds | before mean 35 / 35 ms, after 31 / 34 ms. p95 48-49 ms before, 56-57 ms after |

### Load test, per run (p95 / throughput / mean, 200 requests each, 0 HTTP errors in every run)

| Run | Before (sequential writes) | After (parallel writes) |
|---|---|---|
| 1 | 837 ms / 24.8 req/s / 530 ms | 572 ms / 27.8 req/s / 422 ms |
| 2 | 989 ms / 26.3 req/s / 517 ms | 670 ms / 25.2 req/s / 411 ms |
| 3 | 815 ms / 27.5 req/s / 477 ms | 20094 ms / 7.5 req/s / 2318 ms (all 20 users stalled ~20 s at once, no server errors) |
| 4 | 921 ms / 22.9 req/s / 545 ms | 1392 ms / 20.6 req/s / 715 ms |
| 5 | 3041 ms / 16.9 req/s / 1052 ms | 1015 ms / 7.3 req/s / 1432 ms |
| **Median** | **p95 921 ms, 24.8 req/s** | **p95 1015 ms, 20.6 req/s** |

Round-1 reference: p95 1074 ms, ~18 req/s.

**Honest reading:** the change did not produce a measurable load-test win on this machine. The first two pairs favour the new code (p95 −30%), the medians favour the old code, and the spread between runs of the *same* jar (572 ms to 20 s) is far larger than any difference between the jars. Two findings explain why:
1. **The writes were never the main cost.** With one client, a whole receipt POST takes ~35 ms, so running three writes in parallel can save only a few ms. The 1 s p95 in round 1 is queueing under 20 concurrent users, not the sum of the write latencies. The round-1 diagnosis ("sequential writes cause the 1.07 s p95") was wrong.
2. **Big stalls are outside the app.** In one extra diagnostic run, JMeter reported p95 69 s while the app's own `http.server.requests` timer for the same 200 requests had mean 1.04 s and max 3.3 s, and Mongo inserts averaged 24 ms. Another run had a 65 s gap between two requests of one JMeter thread with no slow response. So these stalls are in the path JMeter (container) → `host.docker.internal` → the Mac, or in the busy VM, not in the service.
Also seen: with `-Djdk.tracePinnedThreads=short`, the AWS SDK's Apache HTTP client pins virtual threads (`synchronized` in `AbstractConnPool`, JDK 21). It didn't cause a failure, but it limits how much virtual threads help with this client.

Kept the parallel writes anyway: the code is correct, tested, and never slower for one client. Next step to actually find the p95 bottleneck: run JMeter on the host (not in Docker), stop the unrelated k8s stack, and add per-store timers (DynamoDB/S3/SQS) to see which dependency queues under load; try the AWS CRT or URL-connection HTTP client to avoid pinning.

## What broke this round (and the fix)

15. **First `mvnw verify` failed for environment reasons**: `ElasticsearchReceiptClientIT` timed out waiting for ES to start (60 s), and `ReceiptFlowIT` hung for 15+ min in the test's HTTP client with no request in flight on the server (thread dump: all Tomcat threads idle). The compose stack (LocalStack + Lambda containers, Mongo, Azurite, Prometheus, WireMock) and the k8s stack were all running in the 5.8 GB VM. Fix: `docker compose down` before `mvnw verify` (tests start their own containers); the rerun passed completely (step 23).
16. **Map order in the error**: `ReceiptWriteException.failedStores()` used `Map.copyOf`, which doesn't keep insertion order, so the test expecting `[dynamodb, mongo]` got `[mongo, dynamodb]`. Fix: unmodifiable `LinkedHashMap` copy.
17. **Port clash avoided**: a local Postgres already listens on 5432, so compose publishes Postgres on 5433 and `application.yml` points there.
