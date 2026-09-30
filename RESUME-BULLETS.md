# Resume bullets — misc-lab (Projects section)

**misc-lab — multi-currency receipt service (Java 21, Spring Boot, AWS via LocalStack, React)**

- Built a Spring Boot 3 receipt service that converts payments through an external FX API and writes each receipt to DynamoDB, S3 and a MongoDB audit log, then publishes a `ReceiptCreated` event to SQS consumed by a long-polling worker; verified end to end with a Testcontainers integration test running LocalStack (S3/SQS/DynamoDB), MongoDB and WireMock.
- Provisioned the AWS stack with Terraform on LocalStack (8 resources: S3 bucket, SQS queue, DynamoDB table, IAM role, Lambda, permission and S3→Lambda notification) and wrote a Java Lambda triggered by S3 uploads that logged `processed 1 object(s), 164 bytes` in 502 ms.
- Hardened the FX dependency with Resilience4j retry, circuit breaker and HTTP read/connect timeouts plus a fallback rate; tests prove retry count, timeout fallback and short-circuiting, and a live test with the FX mock stopped showed the breaker going `OPEN` and receipts still issuing at the fallback rate (11.90 USD instead of 12.00 USD).
- Added a batch-conversion endpoint that fans out FX lookups with `CompletableFuture` on a virtual-thread-per-task executor, with a latch-based unit test that deadlocks unless all 20 conversions really run concurrently.
- Instrumented the service with Micrometer/Prometheus (custom counters and an `fx_call` latency histogram), ECS JSON structured logs, and multi-window SLO burn-rate alerts (99.5% availability, 14.4x/6x burn) — 3 alerts and 4 recording rules loaded in Prometheus; load-tested with JMeter (20 users × 10 loops: 200 requests, 0 HTTP errors, ~18 req/s, p95 1074 ms) and documented the missed 1 s latency target and its sequential-write root cause.
- Built a React + TypeScript (Vite) front end with a custom `useReceipts` hook covered by 4 Vitest/Testing Library tests, and a Selenium Page Object UI test that creates a receipt in Chromium running in Testcontainers; wrote a GitHub Actions workflow for backend, frontend, Terraform validate and promtool checks.
- Added a standalone Angular 19 component that looks up a receipt by id against the same Spring Boot REST API (`frontend-angular/`; Vitest on the HTTP client). Lab-level Angular only — not a routed enterprise app.
- Archived receipt JSON to Azure Blob Storage with the Azure SDK against Azurite (local emulator). Not AKS, App Service, or a paid Azure account.
- Exposed the same receipt lookup as GraphQL (`receipt(id)` on `/graphql`, money as strings) and as a protobuf `GetReceipt` gRPC service; both are adapters over `ReceiptService`, covered by slice/unit tests. A tiny Angular standalone component calls the REST GET for Citi/Broadridge-style UI titles (not a full Angular app).

## Interview talking points (from the 10 fixes in RUN-RESULTS.md)

- **Container runtime plumbing:** Testcontainers couldn't find Rancher Desktop's Docker socket, and docker-java's default API 1.32 was rejected by Docker 29 (min 1.41) — fixed with `DOCKER_HOST`/socket override and `api.version=1.44`.
- **Metrics gotchas:** `/actuator/prometheus` was 404 in `@SpringBootTest` until `@AutoConfigureObservability`; a counter named `receipts_created` silently became `receipts_total` because OpenMetrics reserves the `_created` suffix — renamed to `receipts_issued`.
- **Container-to-host networking:** `host-gateway` pointed at the Rancher VM, not the Mac, so Prometheus/JMeter used `host.docker.internal`; in Selenium, `exposeHostPorts` in `@BeforeAll` ran after `@Container` startup (`ERR_NAME_NOT_RESOLVED`) — moved to a static initializer.
- **Emulator state is ephemeral:** a LocalStack restart wiped resources, so Terraform apply is re-run from fresh state.
- **Apple silicon images:** `standalone-chrome` is amd64-only; switched to multi-arch `selenium/standalone-chromium`.
- **Browser security in tests:** Vite's DNS-rebinding protection blocked `host.testcontainers.internal` (fixed via `allowedHosts`), and a forwarded `Origin` header caused CORS 403s — the dev proxy strips `Origin` since proxied calls are same-origin.
- **Honest perf finding:** p95 ~1.07 s because S3 → DynamoDB → Mongo → SQS run sequentially; next step is parallelising the independent writes with `CompletableFuture` and re-measuring.
