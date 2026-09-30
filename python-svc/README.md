# receipt-insights (Python / FastAPI)

**What:** a small read-only service next to the Java backend. `GET /insights/summary?top=3` returns receipt count,
total USD, totals per original currency, and top payers by USD. It reads receipts from the backend's
`GET /api/receipts`. The same logic runs as a CLI (`receipt-insights --url http://localhost:8090` or
`receipt-insights --stdin < receipts.json`).

**Why:** Python shows up in ~22 of my target JDs. Analytics and reporting is a common job for a small Python service beside a Java core.

**Design choices**
- `insights.py` is pure (no I/O), so both the API and the CLI reuse it and the tests don't need a network.
- Money is `Decimal`. JSON is parsed with `parse_float=Decimal` so amounts never pass through binary float.
- The backend client is a FastAPI dependency (`Depends(get_api)`), so tests swap it with `dependency_overrides`.
- Tests use a real `httpx.Client` with `httpx.MockTransport`. That exercises the URL, query params and JSON parsing, and only the network is faked.
- Backend errors become `502` (upstream failure), not `500`.

```bash
python3 -m venv .venv && .venv/bin/pip install -e '.[dev]'
.venv/bin/pytest -q                                   # 7 tests
.venv/bin/uvicorn receipt_insights.app:app --port 8000
docker build -t receipt-insights . && docker run -p 8000:8000 receipt-insights
```

**How I'd explain it in an interview:** "I added a small FastAPI service that reads receipts from the Spring Boot API
and serves a summary. I kept the summary logic as a pure function so the HTTP layer and the CLI share it. I used Decimal
end to end, because float sums drift. I injected the HTTP client as a dependency so tests can swap in a mock transport. I ran it live against the Java service and it summed 3 receipts correctly." Lab scale only: one process, no auth, no pagination beyond `limit`.
