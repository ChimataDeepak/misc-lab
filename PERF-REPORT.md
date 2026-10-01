# Performance tuning report: `GET /api/statements/{payerId}`

"Latest 20 receipts of a payer, each with its line items" on Postgres 16 (Testcontainers), Spring Boot 3 / Hibernate 6,
Corretto 21, Apple-silicon laptop. Data: 2,000 payers, 400,000 receipts, 1,200,000 line items. Traffic: 32 closed-loop
clients, 80% of requests on 50 "hot" payers, 10 s warm-up, 30 s measured window.
Harness: `backend/src/test/java/com/deepak/misclab/statement/perf/StatementPerfBench.java` (run: `perf/statement-bench.sh`).
Raw outputs: `perf/results/`.

## Headline (final clean run, `perf/results/statement-final.md`)

| Step | Change | req/s | p50 ms | p95 ms | p99 ms | SQL/req |
|---|---|---|---|---|---|---|
| S0 | baseline: lazy N+1, no indexes, pool 10 | 1.7 | 17,801 | 22,636 | 23,357 | 21.6 |
| S1 | + composite index + FK index (V3) | 318.8 | 96.4 | 144.0 | 176.1 | 21.0 |
| S2 | + N+1 removed (ids query + fetch join) | 1,494.9 | 19.8 | 35.3 | 48.5 | 2.0 |
| S3 | pool 4 / pool 20 (vs 10) | 1,294.6 / 1,803.2 | 23.2 / 17.1 | 36.5 / 28.4 | 48.6 / 34.0 | 2.0 |
| S4 | + 5 s Caffeine cache, evict-after-commit | 18,093.5 | 1.5 | 3.6 | 6.2 | 0.0 (98% hit) |

End to end: baseline -> S2 (no cache, always fresh) is ~880x throughput and p99 23.4 s -> 48.5 ms.
S2 -> S4 (cache) is another ~12x throughput, p99 48.5 -> 6.2 ms.

## 1. Missing indexes (EXPLAIN ANALYZE, `perf/results/index-tiebreak-and-explain.md`)

| Query | No index | With index |
|---|---|---|
| latest 20 receipt ids of a payer | Parallel Seq Scan + top-N sort, 8,336 buffers, 39.0 ms | Index Only Scan, 5 buffers, 0.10 ms |
| lines of one receipt (FK lookup) | Parallel Seq Scan of 1.2 M lines, 81.7 ms | Index Scan, 11 buffers, ~0.1 ms |

Postgres does not index foreign-key columns automatically; that one missing index made every lazy line load a full scan.
Control `N-noindex-join` (fetch join but no indexes): 8.8 req/s, p99 5.6 s - fixing N+1 alone is not enough.

Index-column detail: first draft `(payer_id, created_at DESC)` left an Incremental Sort node (24 buffers) because the
query tie-breaks on `id DESC`; adding `id DESC` made it Index Only Scan (5 buffers). In the load test the two were
within noise of each other (paired runs: 560/655 vs 428/933 req/s) - correct plan, no measurable end-to-end gain. Kept for the plan, not claimed as a speedup.

## 2. N+1 queries
Naive repository method + LAZY `lines` = 1 + 20 queries per request (measured 21.0 SQL/req by Hibernate statistics).
Fix: query 1 pages ids in SQL (`LIMIT` served by the index), query 2 `left join fetch` lines for those ids.
(Fetch join + LIMIT in one query would page in memory, HHH90003004.) 21 -> 2 SQL/req, 4.7x req/s, p99 176 -> 48.5 ms.
Correctness guarded by `StatementServiceIT` (same statement both ways; statement count asserted).

## 3. HikariCP pool size - honest: inconclusive above 10
Pool 4 is clearly worse in the earlier run (489 req/s, p99 168 ms, 28 threads waiting) and slightly worse in the final run.
Pool 20 / 40 vs 10 flipped between runs (run A: 20 -> 1,272 req/s, 40 -> 1,187 vs 10 -> 1,702; final run: 20 -> 1,803 vs 10 -> 1,495).
Conclusion: below ~cores x 2 the pool starves; above 10 the bottleneck is Postgres CPU on the same laptop, so a bigger pool just
moves the queue. Default 10 kept.

## 4. Profiling (JFR, `perf/results/jfr-profiles.md`)
After S2, CPU samples are dominated by the Tomcat filter chain / Jackson / HTTP client sharing the same machine, not Hibernate;
time-in-park is waiting on Postgres. Next lever was therefore to avoid the DB round-trip entirely for hot payers -> cache.
JFR also showed `InstantSerializerBase.formatValue` (Jackson date formatting) as the top Jackson frame; see JMH below.

## 5. Caching hot reads + JMH of the hot path (`perf/results/jmh.txt`)
Caffeine, 5 s TTL, keyed by payer -> (limit -> statement); a write evicts the payer **after commit** (evicting inside the
transaction lets a concurrent reader re-cache old rows for the whole TTL).

| JMH (avg time) | 1,000 entries | 10,000 entries |
|---|---|---|
| first draft: `keySet().removeIf(k -> k.startsWith(payer+":"))` | 35.3 us | 377.4 us |
| keyed by payer, one `invalidate` | 0.31 us | 0.56 us |

The scan grows linearly with cache size; keyed eviction is O(1) (~670x faster at 10k).
JSON: `new ObjectMapper()` per call 146.7 us vs shared writer 19.4 us per statement. Spring MVC already shares its mapper, so this
is a guard-rail measurement, not a fix applied to the app.

## 6. GC / heap flags - honest: no gain (`perf/results/gc-heap-experiments.md`)
S4-cache with default G1: 11.5k-15.6k req/s. ParallelGC: 11.7k / 4.7k. Generational ZGC: 1.6k / 2.5k. `-Xmx128m`: 1.5k / 1.7k
(more GC, cache hit fell to 88%). Variance between paired runs is large on a shared laptop, but nothing beat the default G1;
ZGC's concurrent work costs CPU that this CPU-bound, sub-ms-pause workload needs. No flag changed.

## What did not help (kept on purpose)
- Parallelising the three receipt writes (earlier round): no gain.
- id tie-break in the index: better plan, no end-to-end delta.
- Pool > 10, GC flags: within noise or worse.
