package com.deepak.misclab.statement.perf;

import com.deepak.misclab.statement.Statement;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

/**
 * JMH: cost of "a payer wrote a receipt, evict their cached statements" with a warm cache.
 *
 * <p>scanEvict = first draft of StatementService: String keys "payerId:limit", evicted with
 * cache.asMap().keySet().removeIf(k -> k.startsWith(payerId + ":")), which walks EVERY entry.
 * <br>keyedEvict = current StatementService: Cache&lt;payerId, Map&lt;limit, Statement&gt;&gt;, evicted with invalidate(payerId).
 *
 * <p>Each op evicts one payer and puts its entry back (so the cache stays at `entries`). Run: perf/jmh.sh
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class StatementCacheEvictionBenchmark {

    @Param({"1000", "10000"})
    int entries;

    static final Statement S = new Statement(1, 0, Map.of(), List.of());
    Cache<String, Statement> stringKeyed;
    Cache<Long, Map<Integer, Statement>> payerKeyed;
    long next;

    @Setup(Level.Trial)
    public void setUp() {
        stringKeyed = Caffeine.newBuilder().maximumSize(100_000).build();
        payerKeyed = Caffeine.newBuilder().maximumSize(100_000).build();
        for (long p = 0; p < entries; p++) {
            stringKeyed.put(p + ":20", S);
            payerKeyed.get(p, k -> new ConcurrentHashMap<>()).put(20, S);
        }
    }

    @Benchmark
    public Object scanEvict() {
        long p = next++ % entries;
        stringKeyed.asMap().keySet().removeIf(k -> k.startsWith(p + ":"));
        stringKeyed.put(p + ":20", S);
        return stringKeyed;
    }

    @Benchmark
    public Object keyedEvict() {
        long p = next++ % entries;
        payerKeyed.invalidate(p);
        payerKeyed.get(p, k -> new ConcurrentHashMap<>()).put(20, S);
        return payerKeyed;
    }
}
