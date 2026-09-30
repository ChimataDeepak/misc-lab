# perf/

## JMeter performance test
- **What I built:** `receipts.jmx` runs 20 threads x 10 loops (200 POSTs, 5s ramp-up) with randomised payloads and three assertions: status 200/201, body contains `receiptId`, and latency under 1s. `./run.sh` runs it headless in `justb4/jmeter` and writes an HTML dashboard to `perf/report/` (override with `HOST=`/`PORT=`).
- **Why it matters:** functional tests show that it works; load tests show how it behaves under concurrency (throughput, p90/p99, error %).
- **Interview line:** "I ran JMeter non-GUI in Docker, 20 concurrent users, with assertions on status, body and a 1s SLA, then read the p95 and error rate from the HTML report and correlated them with the Prometheus fx_call_seconds timer."
