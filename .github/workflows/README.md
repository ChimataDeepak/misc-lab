# CI (GitHub Actions)
- **What I built:** `ci.yml` has four parallel jobs: backend `mvnw verify` (unit tests plus Testcontainers `*IT`, since runners have Docker), frontend (`npm ci`, test, build), terraform (`fmt -check`, `validate`) and observability (`promtool`, `compose config`).
- **Why it matters:** every push proves that the code, the infrastructure definitions and the alert rules are all valid, not just the Java code.
- **Interview line:** "My pipeline gates app code, IaC and monitoring config together; ITs use real containers on the runner instead of mocks."
