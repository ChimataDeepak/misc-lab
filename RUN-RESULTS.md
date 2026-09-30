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
