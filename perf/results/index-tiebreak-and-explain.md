JVM: OpenJDK 64-Bit Server VM 21.0.11, args: [], concurrency 32, warm-up 10 s, measured 30 s

| Scenario | fetch | indexes | pool | cache | req | err | req/s | p50 ms | p95 ms | p99 ms | max ms | SQL/req | max waiting for conn | cache hit |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| S2-idx2col | join | yes | 10 | 0s | 16799 | 0 | 559.9 | 50.2 | 107.9 | 172.5 | 400.0 | 2.0 | 22 | 0% |
| S2-fetchjoin | join | yes | 10 | 0s | 12846 | 0 | 428.2 | 65.8 | 149.7 | 230.2 | 476.7 | 2.0 | 22 | 0% |
| S2-idx2col | join | yes | 10 | 0s | 19645 | 0 | 654.8 | 41.2 | 94.3 | 212.0 | 860.7 | 2.0 | 22 | 0% |
| S2-fetchjoin | join | yes | 10 | 0s | 27994 | 0 | 933.1 | 31.2 | 59.3 | 97.0 | 414.2 | 2.0 | 22 | 0% |
| S3-pool20 | join | yes | 20 | 0s | 32811 | 0 | 1093.6 | 24.7 | 58.0 | 118.6 | 401.2 | 2.0 | 12 | 0% |
| S4-cache | join | yes | 10 | 5s | 495401 | 0 | 16513.3 | 1.6 | 4.1 | 7.9 | 123.4 | 0.0 | 22 | 98% |

#### EXPLAIN ANALYZE, indexes OFF
`SELECT id FROM ledger_receipts WHERE payer_id = 7 ORDER BY created_at DESC, id DESC LIMIT 20`
```
Limit  (cost=11333.57..11335.90 rows=20 width=16) (actual time=34.822..38.986 rows=20 loops=1)
  Buffers: shared hit=8336
  ->  Gather Merge  (cost=11333.57..11352.93 rows=166 width=16) (actual time=34.820..38.981 rows=20 loops=1)
        Workers Planned: 2
        Workers Launched: 2
        Buffers: shared hit=8336
        ->  Sort  (cost=10333.54..10333.75 rows=83 width=16) (actual time=27.776..27.779 rows=17 loops=3)
              Sort Key: created_at DESC, id DESC
              Sort Method: top-N heapsort  Memory: 26kB
              Buffers: shared hit=8336
              Worker 0:  Sort Method: top-N heapsort  Memory: 26kB
              Worker 1:  Sort Method: quicksort  Memory: 25kB
              ->  Parallel Seq Scan on ledger_receipts  (cost=0.00..10331.33 rows=83 width=16) (actual time=1.464..25.809 rows=67 loops=3)
                    Filter: (payer_id = 7)
                    Rows Removed by Filter: 133267
                    Buffers: shared hit=8248
Planning Time: 0.100 ms
Execution Time: 39.016 ms
```

#### EXPLAIN ANALYZE, indexes OFF
`SELECT * FROM ledger_receipt_lines WHERE receipt_id = (SELECT max(id) FROM ledger_receipts WHERE payer_id = 7)`
```
Gather  (cost=1225.44..16299.74 rows=3 width=31) (actual time=77.024..81.712 rows=3 loops=1)
  Workers Planned: 2
  Params Evaluated: $1
  Workers Launched: 2
  Buffers: shared hit=4138 read=6705
  InitPlan 2 (returns $1)
    ->  Result  (cost=225.43..225.44 rows=1 width=8) (actual time=1.609..1.610 rows=1 loops=1)
          Buffers: shared hit=2019
          InitPlan 1 (returns $0)
            ->  Limit  (cost=0.42..225.43 rows=1 width=8) (actual time=1.605..1.605 rows=1 loops=1)
                  Buffers: shared hit=2019
                  ->  Index Scan Backward using ledger_receipts_pkey on ledger_receipts  (cost=0.42..44777.43 rows=199 width=8) (actual time=1.603..1.603 rows=1 loops=1)
                        Index Cond: (id IS NOT NULL)
                        Filter: (payer_id = 7)
                        Rows Removed by Filter: 1994
                        Buffers: shared hit=2019
  ->  Parallel Seq Scan on ledger_receipt_lines  (cost=0.00..15074.00 rows=1 width=31) (actual time=46.007..66.943 rows=1 loops=3)
        Filter: (receipt_id = $1)
        Rows Removed by Filter: 399999
        Buffers: shared hit=2119 read=6705
Planning Time: 0.106 ms
Execution Time: 81.737 ms
```

#### EXPLAIN ANALYZE, indexes ON (payer_id, created_at DESC)
`SELECT id FROM ledger_receipts WHERE payer_id = 7 ORDER BY created_at DESC, id DESC LIMIT 20`
```
Limit  (cost=4.43..84.87 rows=20 width=16) (actual time=0.076..0.079 rows=20 loops=1)
  Buffers: shared hit=24
  ->  Incremental Sort  (cost=4.43..804.86 rows=199 width=16) (actual time=0.075..0.076 rows=20 loops=1)
        Sort Key: created_at DESC, id DESC
        Presorted Key: created_at
        Full-sort Groups: 1  Sort Method: quicksort  Average Memory: 25kB  Peak Memory: 25kB
        Buffers: shared hit=24
        ->  Index Scan using ledger_receipts_payer_created_idx on ledger_receipts  (cost=0.42..795.90 rows=199 width=16) (actual time=0.020..0.062 rows=21 loops=1)
              Index Cond: (payer_id = 7)
              Buffers: shared hit=24
Planning Time: 0.109 ms
Execution Time: 0.100 ms
```

#### EXPLAIN ANALYZE, indexes ON (payer_id, created_at DESC, id DESC)
`SELECT id FROM ledger_receipts WHERE payer_id = 7 ORDER BY created_at DESC, id DESC LIMIT 20`
```
Limit  (cost=0.42..1.18 rows=20 width=16) (actual time=0.031..0.041 rows=20 loops=1)
  Buffers: shared hit=5
  ->  Index Only Scan using ledger_receipts_payer_created_idx on ledger_receipts  (cost=0.42..7.89 rows=198 width=16) (actual time=0.029..0.037 rows=20 loops=1)
        Index Cond: (payer_id = 7)
        Heap Fetches: 0
        Buffers: shared hit=5
Planning Time: 0.221 ms
Execution Time: 0.104 ms
```

#### EXPLAIN ANALYZE, indexes ON (payer_id, created_at DESC, id DESC)
`SELECT * FROM ledger_receipt_lines WHERE receipt_id = (SELECT max(id) FROM ledger_receipts WHERE payer_id = 7)`
```
Index Scan using ledger_receipt_lines_receipt_idx on ledger_receipt_lines  (cost=8.82..23.92 rows=3 width=31) (actual time=0.078..0.084 rows=3 loops=1)
  Index Cond: (receipt_id = $1)
  Buffers: shared hit=11
  InitPlan 1 (returns $1)
    ->  Aggregate  (cost=8.38..8.39 rows=1 width=8) (actual time=0.051..0.052 rows=1 loops=1)
          Buffers: shared hit=5
          ->  Index Only Scan using ledger_receipts_payer_created_idx on ledger_receipts  (cost=0.42..7.89 rows=198 width=8) (actual time=0.019..0.038 rows=200 loops=1)
                Index Cond: (payer_id = 7)
                Heap Fetches: 0
                Buffers: shared hit=5
Planning Time: 0.131 ms
Execution Time: 0.117 ms
```
