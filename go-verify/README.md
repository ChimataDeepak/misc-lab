# go-verify (Go)

**What:** a small Go CLI that verifies the payment webhook signatures the Java backend sends
(`Misclab-Signature: t=<unix>,v1=<hex HMAC-SHA256(secret, "<t>.<body>")>`). It is the merchant side of the webhook contract, written in a second language.

```bash
echo -n "$BODY" | go run . -secret "$SECRET" -header "$SIG"   # prints OK (exit 0) or REJECTED: <reason> (exit 1)
go test -v ./...
```

**Tests:** these share a fixed test vector with `WebhookSignatureTest.knownVector` on the Java side, so Java and Go must produce the same bytes. They also cover tampered body, wrong secret, moved timestamp, too old, from the future, and malformed header. The MAC compare uses `hmac.Equal` (constant time).

**Where it ran:** Go is not installed on this Mac (no Homebrew either), so the tests ran in `golang:1.23-alpine` via Docker:
`docker run --rm -v "$PWD":/src -w /src golang:1.23-alpine go test -v ./...` → PASS. CI uses `actions/setup-go`.

**How I'd explain it in an interview:** "Webhook signing is a contract between two parties. I wrote the verifier in Go and pinned
both implementations to one test vector, so a change on either side breaks a test. The timestamp is inside the MAC,
so an attacker can't pair an old body with a fresh timestamp. The receiver also enforces a time window." Scope: this
CLI checks signature and timestamp only. The event-id replay cache lives in the Java receiver (`WebhookVerifier`).
