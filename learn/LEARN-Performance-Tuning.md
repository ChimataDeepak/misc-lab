# Learn: Performance tuning (measured, not guessed)

## What it is
Making a service answer faster (latency: p50/p95/p99) and/or answer more requests per second (throughput), by
finding the ONE thing that is slowest right now (the bottleneck), fixing it, and measuring again. Repeat.

## Why it matters
Users feel p99, not the average. One slow query under load queues every request behind it (see the baseline: 23 s p99).
Banks/fintechs (Zeta, Razorpay...) ask for it because the same hardware must serve more traffic within an SLA.

## The loop (how)
1. **Measure a baseline** with a realistic load test (here: 32 clients, hot/cold payer mix, warm-up, then measure).
2. **Find the bottleneck** with evidence: EXPLAIN ANALYZE for SQL, Hibernate statistics for query counts, JFR for CPU / blocking.
3. **Change one thing**, re-run the same test, keep it only if the numbers move.
4. Write down what did NOT help too (pool > 10, GC flags, index tie-break).

## Where in the code
| Concept | File:line |
|---|---|
| N+1 (before) | `backend/src/main/java/com/deepak/misclab/statement/LedgerReceiptRepository.java:13`, lazy load at `StatementService.java:80` |
| Fix: page ids then fetch join | `LedgerReceiptRepository.java:17` and `:24`, used at `StatementService.java:65-67` |
| Indexes | `backend/src/main/resources/db/migration/V3__statement_indexes.sql` |
| Cache (Caffeine, keyed by payer) | `StatementService.java:53` |
| Evict after commit | `StatementService.java:93`, `:101-104` |
| Load test + JFR + EXPLAIN harness | `backend/src/test/java/com/deepak/misclab/statement/perf/StatementPerfBench.java` |
| JMH microbenchmarks | `.../statement/perf/StatementCacheEvictionBenchmark.java`, `StatementJsonBenchmark.java` |
| Correctness test | `backend/src/test/java/com/deepak/misclab/statement/StatementServiceIT.java:58,70,76` |

## How it was verified (numbers in PERF-REPORT.md)
- Indexes: 1.7 -> 319 req/s; lines lookup 81.7 ms -> ~0.1 ms (EXPLAIN ANALYZE).
- N+1 fix: 21 -> 2 SQL/request, 319 -> 1,495 req/s, p99 176 -> 48.5 ms.
- Cache: 18,094 req/s, p99 6.2 ms, 98% hit rate.
- JMH: keyed eviction 0.56 us vs scan 377 us at 10k entries.
- `StatementServiceIT` proves the fast path returns the same statement and that a write is visible immediately.

## Plain-language ideas
- **Index** = the book's index: jump to the page instead of reading the whole book. Postgres does NOT index foreign keys for you.
- **N+1** = 1 query for the list + 1 per row. 20 rows -> 21 round trips. Fetch join = get it all in one go.
- **Why not fetch join + LIMIT in one query?** The join multiplies rows, so Hibernate would load everything and page in memory.
- **Connection pool** = number of DB connections shared by all threads. Too small -> threads queue for a connection; too big -> the DB itself queues. Start near cores x 2 and measure.
- **Cache** = keep a recent answer in memory. Hard part is staleness: evict when data changes, and do it *after* the commit.
- **p99** = 99% of requests are faster than this. Averages hide the slow tail.
- **JMH** = the right way to time small Java code (handles JIT warm-up, dead-code elimination). `System.nanoTime` loops lie.
- **JFR** = flight recorder built into the JDK; low overhead profile of CPU, locks, GC.

## Interview Q&A
1. *How do you find a slow query?* Load-test, check SQL/request (Hibernate stats or pg_stat_statements), EXPLAIN (ANALYZE, BUFFERS): look for Seq Scan on a big table, sorts, buffer counts.
2. *What is N+1 and how do you fix it?* Lazy collection touched per row. Fix with fetch join / @EntityGraph / batch fetching (`hibernate.default_batch_fetch_size`) or a DTO projection.
3. *Why did a bigger Hikari pool not help?* The DB was the bottleneck (same CPU); more connections only move the queue. Pool size ~ (cores x 2) + spindles is the starting heuristic.
4. *Cache invalidation race?* If you evict inside the transaction, a reader can reload pre-commit data and cache it for the TTL. Evict in afterCommit.
5. *Did GC tuning help?* No - default G1 was best here; ZGC traded CPU for pause times we did not have. Measure before touching flags.
6. *p99 vs average?* Tail latency drives SLA breaches and user experience; queues amplify it.

## Exercises
1. Replace the two-query fix with `@BatchSize(size = 20)` on `lines`; compare SQL/req and p99.
2. Turn on `pg_stat_statements` in the container and find the top query by total time in S0.
3. Run `PERF_JFR=true perf/statement-bench.sh S2-fetchjoin` and explain the top 3 frames.
4. Lower the cache TTL to 500 ms; what happens to hit rate and p99?
5. Add a covering index for query 2 (`INCLUDE (description, amount_minor)`) and check whether EXPLAIN changes to Index Only Scan.
