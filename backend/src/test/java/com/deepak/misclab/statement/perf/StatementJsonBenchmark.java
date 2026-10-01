package com.deepak.misclab.statement.perf;

import com.deepak.misclab.statement.Statement;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

/**
 * JMH: is JSON serialization of a 20-receipt statement worth tuning? Measures Jackson writing the response DTO,
 * with a fresh ObjectMapper per call (a classic mistake: rebuilds serializer caches every time) vs a shared,
 * pre-built ObjectWriter (what Spring MVC effectively does). Run: perf/jmh.sh
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class StatementJsonBenchmark {

    Statement statement;
    ObjectWriter shared;

    @Setup
    public void setUp() {
        List<Statement.Item> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            items.add(new Statement.Item(1000 + i, "merchant-" + i, 5_000 + i, "INR", Instant.parse("2026-09-30T10:15:30Z").minusSeconds(i),
                    List.of(new Statement.Line("item-1", 1000), new Statement.Line("item-2", 2000), new Statement.Line("item-3", 2000 + i))));
        }
        statement = new Statement(7, 20, Map.of("INR", 100_190L), items);
        shared = newMapper().writerFor(Statement.class);
    }

    static ObjectMapper newMapper() { return new ObjectMapper().registerModule(new JavaTimeModule()); }

    @Benchmark
    public byte[] sharedWriter() throws Exception { return shared.writeValueAsBytes(statement); }

    @Benchmark
    public byte[] newMapperPerCall() throws Exception { return newMapper().writeValueAsBytes(statement); }
}
