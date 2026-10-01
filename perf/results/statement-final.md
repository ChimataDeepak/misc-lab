JVM: OpenJDK 64-Bit Server VM 21.0.11, args: [], concurrency 32, warm-up 10 s, measured 30 s

| Scenario | fetch | indexes | pool | cache | req | err | req/s | p50 ms | p95 ms | p99 ms | max ms | SQL/req | max waiting for conn | cache hit |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| S0-baseline | naive | no | 10 | 0s | 50 | 0 | 1.7 | 17800.8 | 22636.4 | 23356.7 | 23356.7 | 21.6 | 22 | 0% |
| S1-indexes | naive | yes | 10 | 0s | 9565 | 0 | 318.8 | 96.4 | 144.0 | 176.1 | 288.7 | 21.0 | 22 | 0% |
| S2-fetchjoin | join | yes | 10 | 0s | 44851 | 0 | 1494.9 | 19.8 | 35.3 | 48.5 | 182.7 | 2.0 | 22 | 0% |
| S3-pool4 | join | yes | 4 | 0s | 38845 | 0 | 1294.6 | 23.2 | 36.5 | 48.6 | 99.2 | 2.0 | 28 | 0% |
| S3-pool20 | join | yes | 20 | 0s | 54096 | 0 | 1803.2 | 17.1 | 28.4 | 34.0 | 55.0 | 2.0 | 12 | 0% |
| S4-cache | join | yes | 10 | 5s | 542841 | 0 | 18093.5 | 1.5 | 3.6 | 6.2 | 61.2 | 0.0 | 22 | 98% |

#### EXPLAIN ANALYZE, indexes OFF
`SELECT id FROM ledger_receipts WHERE payer_id = 7 ORDER BY created_at DESC, id DESC LIMIT 20`
```
Limit  (cost=11333.57..11335.90 rows=20 width=16) (actual time=12.419..13.718 rows=20 loops=1)
  Buffers: shared hit=8288 read=48
  ->  Gather Merge  (cost=11333.57..11352.93 rows=166 width=16) (actual time=12.418..13.715 rows=20 loops=1)
        Workers Planned: 2
        Workers Launched: 2
        Buffers: shared hit=8288 read=48
        ->  Sort  (cost=10333.54..10333.75 rows=83 width=16) (actual time=9.269..9.270 rows=17 loops=3)
              Sort Key: created_at DESC, id DESC
              Sort Method: top-N heapsort  Memory: 25kB
              Buffers: shared hit=8288 read=48
              Worker 0:  Sort Method: top-N heapsort  Memory: 26kB
              Worker 1:  Sort Method: quicksort  Memory: 25kB
              ->  Parallel Seq Scan on ledger_receipts  (cost=0.00..10331.33 rows=83 width=16) (actual time=0.614..9.227 rows=67 loops=3)
                    Filter: (payer_id = 7)
                    Rows Removed by Filter: 133267
                    Buffers: shared hit=8200 read=48
Planning Time: 0.057 ms
Execution Time: 13.733 ms
```

#### EXPLAIN ANALYZE, indexes OFF
`SELECT * FROM ledger_receipt_lines WHERE receipt_id = (SELECT max(id) FROM ledger_receipts WHERE payer_id = 7)`
```
Gather  (cost=1224.11..16298.41 rows=3 width=31) (actual time=13.459..32.957 rows=3 loops=1)
  Workers Planned: 2
  Params Evaluated: $1
  Workers Launched: 2
  Buffers: shared hit=4397 read=6446
  InitPlan 2 (returns $1)
    ->  Result  (cost=224.10..224.11 rows=1 width=8) (actual time=1.176..1.177 rows=1 loops=1)
          Buffers: shared hit=2019
          InitPlan 1 (returns $0)
            ->  Limit  (cost=0.42..224.10 rows=1 width=8) (actual time=1.174..1.175 rows=1 loops=1)
                  Buffers: shared hit=2019
                  ->  Index Scan Backward using ledger_receipts_pkey on ledger_receipts  (cost=0.42..44736.23 rows=200 width=8) (actual time=1.173..1.173 rows=1 loops=1)
                        Index Cond: (id IS NOT NULL)
                        Filter: (payer_id = 7)
                        Rows Removed by Filter: 1994
                        Buffers: shared hit=2019
  ->  Parallel Seq Scan on ledger_receipt_lines  (cost=0.00..15074.00 rows=1 width=31) (actual time=18.232..26.826 rows=1 loops=3)
        Filter: (receipt_id = $1)
        Rows Removed by Filter: 399999
        Buffers: shared hit=2378 read=6446
Planning Time: 0.165 ms
Execution Time: 32.974 ms
```

#### EXPLAIN ANALYZE, indexes ON (payer_id, created_at DESC)
`SELECT id FROM ledger_receipts WHERE payer_id = 7 ORDER BY created_at DESC, id DESC LIMIT 20`
```
Limit  (cost=4.43..84.87 rows=20 width=16) (actual time=0.020..0.022 rows=20 loops=1)
  Buffers: shared hit=24
  ->  Incremental Sort  (cost=4.43..804.85 rows=199 width=16) (actual time=0.020..0.021 rows=20 loops=1)
        Sort Key: created_at DESC, id DESC
        Presorted Key: created_at
        Full-sort Groups: 1  Sort Method: quicksort  Average Memory: 25kB  Peak Memory: 25kB
        Buffers: shared hit=24
        ->  Index Scan using ledger_receipts_payer_created_idx on ledger_receipts  (cost=0.42..795.90 rows=199 width=16) (actual time=0.007..0.015 rows=21 loops=1)
              Index Cond: (payer_id = 7)
              Buffers: shared hit=24
Planning Time: 0.034 ms
Execution Time: 0.042 ms
```

#### EXPLAIN ANALYZE, indexes ON (payer_id, created_at DESC, id DESC)
`SELECT id FROM ledger_receipts WHERE payer_id = 7 ORDER BY created_at DESC, id DESC LIMIT 20`
```
Limit  (cost=0.42..1.17 rows=20 width=16) (actual time=0.005..0.010 rows=20 loops=1)
  Buffers: shared hit=5
  ->  Index Only Scan using ledger_receipts_payer_created_idx on ledger_receipts  (cost=0.42..7.91 rows=199 width=16) (actual time=0.004..0.008 rows=20 loops=1)
        Index Cond: (payer_id = 7)
        Heap Fetches: 0
        Buffers: shared hit=5
Planning Time: 0.029 ms
Execution Time: 0.016 ms
```

#### EXPLAIN ANALYZE, indexes ON (payer_id, created_at DESC, id DESC)
`SELECT * FROM ledger_receipt_lines WHERE receipt_id = (SELECT max(id) FROM ledger_receipts WHERE payer_id = 7)`
```
Index Scan using ledger_receipt_lines_receipt_idx on ledger_receipt_lines  (cost=8.84..23.96 rows=3 width=31) (actual time=0.058..0.060 rows=3 loops=1)
  Index Cond: (receipt_id = $1)
  Buffers: shared hit=11
  InitPlan 1 (returns $1)
    ->  Aggregate  (cost=8.40..8.41 rows=1 width=8) (actual time=0.045..0.045 rows=1 loops=1)
          Buffers: shared hit=5
          ->  Index Only Scan using ledger_receipts_payer_created_idx on ledger_receipts  (cost=0.42..7.91 rows=199 width=8) (actual time=0.007..0.033 rows=200 loops=1)
                Index Cond: (payer_id = 7)
                Heap Fetches: 0
                Buffers: shared hit=5
Planning Time: 0.078 ms
Execution Time: 0.073 ms
```
