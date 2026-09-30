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
| Selenium | `e2e/` | Page Object + Chromium in Testcontainers |
| JMeter | `perf/` | 20-user load test with assertions and HTML report |
| Testcontainers, CI | `backend/…/*IT.java`, `.github/workflows/ci.yml` | LocalStack + Mongo integration tests; CI for all parts |

Proof it runs: **[RUN-RESULTS.md](RUN-RESULTS.md)** — every step, its command, pass/fail and numbers, plus the 10 real problems hit and fixed.

## AI-assisted development (honest note)
Built with Claude Code agents: three builder agents worked in parallel on disjoint folders (backend+lambda, infra+perf+observability+CI, frontend+e2e) against a shared contract (names, ports, metrics). The combined dry run and all the fixes in RUN-RESULTS.md were done step by step with the results checked by hand. Before claiming any skill in an interview, read that folder's README — each ends with "how to explain this in an interview".
