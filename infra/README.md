# infra/

## Terraform (Infrastructure as Code)
- **What I built:** `terraform/` creates the S3 bucket `misc-receipts`, SQS `receipt-events`, DynamoDB `receipts` (PK `receiptId`) and a Java 21 Lambda with an S3 `ObjectCreated` trigger. One flag (`use_localstack`) switches between LocalStack and real AWS (`aws-real.tfvars.example`). Terraform runs in Docker via `./tf.sh` (`init`, `plan`, `apply`).
- **Why it matters:** infra is reviewed, versioned and reproducible like code; `plan` shows the diff before anything changes, and state tracks what exists.
- **Interview line:** "I defined the queue, table, bucket and an S3-triggered Lambda in Terraform, validated against LocalStack, and the same code targets real AWS by flipping one variable. `plan` is my code review for infrastructure."

## docker-compose.yml (repo root)
- **What I built:** LocalStack, Mongo 7, WireMock on 8099 (fake FX API, `wiremock/mappings/rates.json`), Prometheus; Grafana is opt-in via `--profile grafana`.
- **Why it matters:** one command gives every developer the same dependencies without a cloud account.
- **Interview line:** "Compose is my local environment; Testcontainers is my test environment. Both use the same images, so local and test behave alike."
