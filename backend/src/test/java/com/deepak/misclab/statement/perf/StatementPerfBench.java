package com.deepak.misclab.statement.perf;

import com.deepak.misclab.MiscLabApplication;
import com.deepak.misclab.statement.StatementService;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Load-test harness for GET /api/statements/{payerId} (see PERF-REPORT.md). NOT part of mvnw test/verify
 * (class name matches neither *Test nor *IT); run it with perf/statement-bench.sh.
 *
 * <p>For each scenario it boots the real app (Tomcat on a random port) against one seeded Postgres 16 container,
 * drives it with C closed-loop virtual-thread clients for a warm-up and a measured window, and prints
 * p50/p95/p99/max latency, throughput, SQL statements per request (Hibernate statistics), the most threads seen
 * waiting for a Hikari connection, and (with -Dperf.jfr=true) a JFR profile summary of the measured window.
 *
 * <p>System properties: perf.scenarios (comma list of names below), perf.concurrency (32), perf.warmup (10),
 * perf.seconds (30), perf.jfr (false), perf.explain (true).
 */
class StatementPerfBench {

    static final int PAYERS = 2_000, RECEIPTS = 400_000, LINES_PER_RECEIPT = 3, HOT_PAYERS = 50;

    record Scenario(String name, String fetch, boolean indexes, int pool, String cacheTtl, boolean idTiebreak) {
        Scenario(String name, String fetch, boolean indexes, int pool, String cacheTtl) { this(name, fetch, indexes, pool, cacheTtl, true); }
    }

    static final Map<String, Scenario> SCENARIOS = new LinkedHashMap<>();
    static {
        for (Scenario s : List.of(
                new Scenario("S0-baseline", "naive", false, 10, "0s"),        // N+1, no indexes, default pool
                new Scenario("S1-indexes", "naive", true, 10, "0s"),          // + V3 indexes
                new Scenario("S2-fetchjoin", "join", true, 10, "0s"),         // + N+1 -> 2 queries
                new Scenario("S2-idx2col", "join", true, 10, "0s", false),     // control: first index draft (payer_id, created_at DESC), no id tie-break
                new Scenario("S3-pool4", "join", true, 4, "0s"),              // pool sizing experiments
                new Scenario("S3-pool20", "join", true, 20, "0s"),
                new Scenario("S3-pool40", "join", true, 40, "0s"),
                new Scenario("S4-cache", "join", true, 10, "5s"),             // + 5 s Caffeine cache of hot statements
                new Scenario("N-noindex-join", "join", false, 10, "0s"))) {   // control: fetch join WITHOUT indexes
            SCENARIOS.put(s.name(), s);
        }
    }

    record Result(Scenario s, int requests, int errors, double rps, long p50, long p95, long p99, long max,
                  double sqlPerReq, int maxWaiting, double hitRate, String profile) {}

    @Test
    void run() throws Exception {
        String names = System.getProperty("perf.scenarios", "S0-baseline,S1-indexes,S2-fetchjoin,S4-cache");
        int conc = Integer.getInteger("perf.concurrency", 32);
        int warm = Integer.getInteger("perf.warmup", 10), secs = Integer.getInteger("perf.seconds", 30);
        boolean jfr = Boolean.getBoolean("perf.jfr");
        Path out = Path.of("target/perf");
        Files.createDirectories(out);

        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16").withDatabaseName("misclab")
                .withUsername("perf").withPassword("perf")) {
            pg.start();
            List<Result> results = new ArrayList<>();
            StringBuilder explain = new StringBuilder();
            boolean seeded = false;
            for (String name : names.split(",")) {
                Scenario s = Objects.requireNonNull(SCENARIOS.get(name.trim()), "unknown scenario " + name);
                try (ConfigurableApplicationContext ctx = boot(pg, s)) {    // first boot runs Flyway V1-V3
                    if (!seeded) { seed(pg); seeded = true; if (!"false".equals(System.getProperty("perf.explain"))) explain.append(explain(pg)); }
                    setIndexes(pg, s.indexes(), s.idTiebreak());
                    results.add(drive(ctx, s, conc, warm, secs, jfr ? out.resolve(s.name() + ".jfr") : null));
                }
            }
            StringBuilder md = new StringBuilder();
            md.append("JVM: ").append(System.getProperty("java.vm.name")).append(' ').append(System.getProperty("java.version"))
              .append(", args: ").append(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                      .filter(a -> a.startsWith("-X") && !a.startsWith("-XX:+Enable")).toList())
              .append(", concurrency ").append(conc).append(", warm-up ").append(warm).append(" s, measured ").append(secs).append(" s\n\n");
            md.append("| Scenario | fetch | indexes | pool | cache | req | err | req/s | p50 ms | p95 ms | p99 ms | max ms | SQL/req | max waiting for conn | cache hit |\n");
            md.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
            for (Result r : results) {
                md.append(String.format(Locale.ROOT, "| %s | %s | %s | %d | %s | %d | %d | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %d | %.0f%% |%n",
                        r.s().name(), r.s().fetch(), r.s().indexes() ? "yes" : "no", r.s().pool(), r.s().cacheTtl(), r.requests(), r.errors(),
                        r.rps(), r.p50() / 1e3, r.p95() / 1e3, r.p99() / 1e3, r.max() / 1e3, r.sqlPerReq(), r.maxWaiting(), r.hitRate() * 100));
            }
            for (Result r : results) if (r.profile() != null) md.append("\n#### JFR ").append(r.s().name()).append('\n').append(r.profile());
            md.append(explain);
            String stamp = String.valueOf(System.currentTimeMillis());
            Files.writeString(out.resolve("results-" + stamp + ".md"), md);
            System.out.println("\n===== PERF RESULTS =====\n" + md + "===== written to target/perf/results-" + stamp + ".md =====");
        }
    }

    static ConfigurableApplicationContext boot(PostgreSQLContainer<?> pg, Scenario s) {
        return new SpringApplicationBuilder(MiscLabApplication.class).run(
                "--server.port=0",
                "--spring.datasource.url=" + pg.getJdbcUrl(),
                "--spring.datasource.username=perf", "--spring.datasource.password=perf",
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.datasource.hikari.maximum-pool-size=" + s.pool(),
                "--spring.datasource.hikari.minimum-idle=" + s.pool(),
                "--spring.jpa.properties.hibernate.generate_statistics=true",
                "--misclab.statement.fetch=" + s.fetch(),
                "--misclab.statement.cache-ttl=" + s.cacheTtl(),
                "--misclab.aws.init-resources=false", "--misclab.poller.enabled=false",
                "--logging.level.root=WARN", "--logging.structured.format.console=",
                "--logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF");
    }

    static void seed(PostgreSQLContainer<?> pg) throws Exception {
        try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), "perf", "perf"); Statement st = c.createStatement()) {
            long t = System.nanoTime();
            st.execute("INSERT INTO ledger_receipts (payer_id, merchant, amount_minor, currency, created_at) "
                    + "SELECT (g % " + PAYERS + ") + 1, 'merchant-' || (g % 97), 1, (ARRAY['INR','USD','EUR'])[1 + g % 3], "
                    + "now() - make_interval(secs => g) FROM generate_series(1, " + RECEIPTS + ") g");
            st.execute("INSERT INTO ledger_receipt_lines (receipt_id, description, amount_minor) "
                    + "SELECT r.id, 'item-' || k, 100 + ((r.id * k) % 5000) FROM ledger_receipts r, generate_series(1, " + LINES_PER_RECEIPT + ") k");
            st.execute("UPDATE ledger_receipts r SET amount_minor = s.t FROM "
                    + "(SELECT receipt_id, sum(amount_minor) t FROM ledger_receipt_lines GROUP BY receipt_id) s WHERE s.receipt_id = r.id");
            st.execute("VACUUM ANALYZE ledger_receipts");
            st.execute("VACUUM ANALYZE ledger_receipt_lines");
            System.out.printf("seeded %d receipts / %d lines in %d ms%n", RECEIPTS, RECEIPTS * LINES_PER_RECEIPT, (System.nanoTime() - t) / 1_000_000);
        }
    }

    static void setIndexes(PostgreSQLContainer<?> pg, boolean on) throws Exception { setIndexes(pg, on, true); }

    /** idTiebreak=false is the first draft of the index (no trailing id DESC): Postgres then adds an Incremental Sort. */
    static void setIndexes(PostgreSQLContainer<?> pg, boolean on, boolean idTiebreak) throws Exception {
        try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), "perf", "perf"); Statement st = c.createStatement()) {
            st.execute("DROP INDEX IF EXISTS ledger_receipts_payer_created_idx");
            if (on) {
                st.execute("CREATE INDEX ledger_receipts_payer_created_idx ON ledger_receipts (payer_id, created_at DESC"
                        + (idTiebreak ? ", id DESC)" : ")"));
                st.execute("CREATE INDEX IF NOT EXISTS ledger_receipt_lines_receipt_idx ON ledger_receipt_lines (receipt_id)");
            } else {
                st.execute("DROP INDEX IF EXISTS ledger_receipts_payer_created_idx");
                st.execute("DROP INDEX IF EXISTS ledger_receipt_lines_receipt_idx");
            }
            st.execute("ANALYZE ledger_receipts"); st.execute("ANALYZE ledger_receipt_lines");
        }
    }

    /** EXPLAIN (ANALYZE, BUFFERS) of the two statement queries for one payer, without and with the V3 indexes. */
    static String explain(PostgreSQLContainer<?> pg) throws Exception {
        String q1 = "SELECT id FROM ledger_receipts WHERE payer_id = 7 ORDER BY created_at DESC, id DESC LIMIT 20";
        String q2 = "SELECT * FROM ledger_receipt_lines WHERE receipt_id = (SELECT max(id) FROM ledger_receipts WHERE payer_id = 7)";
        StringBuilder sb = new StringBuilder();
        for (int state = 0; state < 3; state++) {                                  // 0 = no indexes, 1 = 2-column draft, 2 = final
            boolean idx = state > 0;
            setIndexes(pg, idx, state == 2);
            for (String q : state == 1 ? List.of(q1) : List.of(q1, q2)) {
                try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), "perf", "perf"); Statement st = c.createStatement()) {
                    for (int i = 0; i < 3; i++) st.executeQuery(q).close();                   // warm the buffer cache first
                    sb.append("\n#### EXPLAIN ANALYZE, indexes ").append(new String[] {"OFF", "ON (payer_id, created_at DESC)", "ON (payer_id, created_at DESC, id DESC)"}[state]).append("\n`").append(q).append("`\n```\n");
                    try (ResultSet rs = st.executeQuery("EXPLAIN (ANALYZE, BUFFERS) " + q)) { while (rs.next()) sb.append(rs.getString(1)).append('\n'); }
                    sb.append("```\n");
                }
            }
        }
        return sb.toString();
    }

    static Result drive(ConfigurableApplicationContext ctx, Scenario s, int conc, int warm, int secs, Path jfrFile) throws Exception {
        int port = ((ServletWebServerApplicationContext) ctx).getWebServer().getPort();
        HikariDataSource ds = ctx.getBean(DataSource.class).unwrap(HikariDataSource.class);
        var stats = ctx.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
        StatementService svc = ctx.getBean(StatementService.class);
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();

        AtomicBoolean measuring = new AtomicBoolean(false), stop = new AtomicBoolean(false);
        ConcurrentLinkedQueue<long[]> samples = new ConcurrentLinkedQueue<>();
        AtomicInteger errors = new AtomicInteger(), maxWaiting = new AtomicInteger();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int w = 0; w < conc; w++) {
                long seed = w * 7919L + 17;
                pool.submit(() -> {
                    SplittableRandom rnd = new SplittableRandom(seed);
                    long[] buf = new long[1 << 16]; int n = 0;
                    while (!stop.get()) {
                        long payer = rnd.nextInt(100) < 80 ? 1 + rnd.nextInt(HOT_PAYERS) : 1 + rnd.nextInt(PAYERS);   // 80% of traffic on 50 hot payers
                        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/statements/" + payer + "?limit=20"))
                                .timeout(Duration.ofSeconds(60)).build();
                        long t0 = System.nanoTime();
                        boolean ok;
                        try { ok = http.send(req, HttpResponse.BodyHandlers.ofByteArray()).statusCode() == 200; } catch (Exception e) { ok = false; }
                        long us = (System.nanoTime() - t0) / 1_000;
                        if (measuring.get()) { if (ok) { buf[n++] = us; if (n == buf.length) { samples.add(Arrays.copyOf(buf, n)); n = 0; } } else errors.incrementAndGet(); }
                    }
                    samples.add(Arrays.copyOf(buf, n));
                    return null;
                });
            }
            Thread sampler = Thread.ofPlatform().daemon().start(() -> {
                while (!stop.get()) {
                    if (measuring.get()) maxWaiting.accumulateAndGet(ds.getHikariPoolMXBean().getThreadsAwaitingConnection(), Math::max);
                    try { Thread.sleep(50); } catch (InterruptedException e) { return; }
                }
            });
            Thread.sleep(warm * 1000L);
            stats.clear();
            Recording rec = null;
            if (jfrFile != null) {
                rec = new Recording(jdk.jfr.Configuration.getConfiguration("profile"));
                rec.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(1)).withStackTrace();
                rec.start();
            }
            long t0 = System.nanoTime();
            measuring.set(true);
            Thread.sleep(secs * 1000L);
            measuring.set(false);
            double elapsed = (System.nanoTime() - t0) / 1e9;
            long sql = stats.getPrepareStatementCount();
            if (rec != null) { rec.stop(); rec.dump(jfrFile); rec.close(); }
            stop.set(true);
            sampler.interrupt();
            pool.shutdown();
            pool.awaitTermination(2, TimeUnit.MINUTES);
            long[] all = samples.stream().flatMapToLong(Arrays::stream).sorted().toArray();
            int reqs = all.length;
            String profile = jfrFile == null ? null : JfrSummary.summarize(jfrFile);
            return new Result(s, reqs, errors.get(), reqs / elapsed, pct(all, 50), pct(all, 95), pct(all, 99), reqs == 0 ? 0 : all[reqs - 1],
                    reqs == 0 ? 0 : (double) sql / reqs, maxWaiting.get(), svc.cacheHitRate(), profile);
        }
    }

    static long pct(long[] sorted, double p) {
        if (sorted.length == 0) return 0;
        return sorted[(int) Math.min(sorted.length - 1, Math.ceil(p / 100.0 * sorted.length) - 1)];
    }

    /** Reads a .jfr file and reports where CPU samples land and where threads block (the evidence for the next fix). */
    static final class JfrSummary {
        static String summarize(Path file) throws Exception {
            Map<String, Integer> cpuTop = new HashMap<>(), cpuApp = new HashMap<>();
            Map<String, Long> parkMs = new HashMap<>();
            long gcPauseMs = 0; int gcs = 0, samplesTotal = 0;
            try (RecordingFile rf = new RecordingFile(file)) {
                while (rf.hasMoreEvents()) {
                    RecordedEvent e = rf.readEvent();
                    String type = e.getEventType().getName();
                    switch (type) {
                        case "jdk.ExecutionSample" -> {
                            if (e.getStackTrace() == null || e.getStackTrace().getFrames().isEmpty()) break;
                            samplesTotal++;
                            cpuTop.merge(frame(e.getStackTrace().getFrames().get(0)), 1, Integer::sum);
                            String owner = firstMatching(e.getStackTrace().getFrames(), "org.hibernate.", "org.postgresql.", "com.fasterxml.jackson.", "com.zaxxer.", "org.apache.catalina.", "org.apache.coyote.", "java.net.http.");
                            cpuApp.merge(owner, 1, Integer::sum);
                        }
                        case "jdk.ThreadPark" -> {
                            if (e.getStackTrace() == null) break;
                            String where = firstMatching(e.getStackTrace().getFrames(), "com.zaxxer.hikari.", "org.postgresql.", "org.apache.tomcat.", "java.net.http.", "jdk.internal.net.");
                            parkMs.merge(where, e.getDuration().toMillis(), Long::sum);
                        }
                        case "jdk.GarbageCollection" -> { gcs++; gcPauseMs += e.getDuration("sumOfPauses").toMillis(); }
                        default -> { }
                    }
                }
            }
            final int total = Math.max(1, samplesTotal);
            StringBuilder sb = new StringBuilder();
            sb.append("CPU samples: ").append(samplesTotal).append(" | GC: ").append(gcs).append(" collections, total pause ").append(gcPauseMs).append(" ms\n\n");
            sb.append("CPU by library (first frame in that library):\n");
            top(cpuApp, 6).forEach(en -> sb.append(String.format(Locale.ROOT, "- %s: %.1f%%%n", en.getKey(), 100.0 * en.getValue() / total)));
            sb.append("\nHottest top-of-stack methods:\n");
            top(cpuTop, 6).forEach(en -> sb.append(String.format(Locale.ROOT, "- %s: %.1f%%%n", en.getKey(), 100.0 * en.getValue() / total)));
            sb.append("\nThread-park time (>=1 ms parks) by where it parked:\n");
            parkMs.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(5)
                    .forEach(en -> sb.append("- ").append(en.getKey()).append(": ").append(en.getValue()).append(" ms\n"));
            return sb.toString();
        }

        static String frame(RecordedFrame f) { return f.getMethod().getType().getName() + "." + f.getMethod().getName(); }

        static String firstMatching(List<RecordedFrame> frames, String... prefixes) {
            for (RecordedFrame f : frames) {
                String t = f.getMethod().getType().getName();
                for (String p : prefixes) if (t.startsWith(p)) return frame(f);
            }
            return "(other: " + frame(frames.get(0)) + ")";
        }

        static <K> List<Map.Entry<K, Integer>> top(Map<K, Integer> m, int n) {
            return m.entrySet().stream().sorted(Map.Entry.<K, Integer>comparingByValue().reversed()).limit(n).toList();
        }
    }
}
