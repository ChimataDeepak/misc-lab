# misc-lab lambda

## 7. AWS Lambda (Java)
- **Built:** `ReceiptUploadedHandler implements RequestHandler<S3Event, String>`: summarises the uploaded objects (count, bytes, keys). Unit-tested with a hand-built `S3Event`; `./mvnw package` builds a shaded (fat) jar at `target/misc-lab-lambda-0.0.1-SNAPSHOT.jar`.
- **Why:** event-driven serverless code: S3 upload triggers compute with no server to manage, pay per invocation.
- **Interview:** "Lambda calls my handler with a typed event; the shade plugin bundles dependencies into one jar because Lambda loads a single artifact. Cold starts in Java are the main trade-off, mitigated by SnapStart or keeping the handler small."
- Handler string: `com.deepak.misclab.lambda.ReceiptUploadedHandler::handleRequest`
