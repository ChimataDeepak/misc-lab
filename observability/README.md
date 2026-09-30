# observability/

## SRE: SLOs and burn-rate alerting
- **What I built:** `prometheus.yml` scrapes `/actuator/prometheus`. `alert-rules.yml` defines a 99.5% availability SLO on `/api/receipts` (errors = 5xx in `http_server_requests_seconds_count`) with multi-window burn-rate alerts: 14.4x over 1h+5m pages, 6x over 6h+30m opens a ticket. There is also a p95 latency alert on `fx_call_seconds`. Checked with `promtool check rules`.
- **Why it matters:** alerting on error-budget burn catches real user impact quickly without paging on every blip. The short window makes the alert clear quickly once the problem is fixed.
- **Interview line:** "A 99.5% SLO gives a 0.5% error budget. I page when we burn it 14.4x faster than sustainable, which is 2% of the month's budget in one hour, and require both a long and a short window to agree so alerts are fast and reset quickly."
- Note: the latency alert needs histogram buckets, so the app's `fx_call_seconds` timer must call `publishPercentileHistogram()`.
