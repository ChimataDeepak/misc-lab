package com.deepak.misclab.statement;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * "Latest N receipts of a payer, with their line items" - the read path tuned in PERF-REPORT.md.
 *
 * <p>misclab.statement.fetch = naive | join  (naive = N+1 lazy loading, join = 2 queries total)
 * <br>misclab.statement.cache-ttl = 0 (off) or e.g. 5s: in-process Caffeine cache of hot statements, evicted on write.
 */
@Service
public class StatementService {
    public static final int MAX_LIMIT = 50;

    private final LedgerReceiptRepository repo;
    private final boolean joinFetch;
    /**
     * payerId -> (limit -> statement). Keyed by payer so a write evicts that payer's entries with ONE O(1) invalidate.
     * The first draft keyed by "payerId:limit" strings and evicted with keySet().removeIf(...), a scan of every cached
     * entry on every write (JMH: StatementCacheEvictionBenchmark, PERF-REPORT.md step 5). null = caching off.
     */
    private final Cache<Long, Map<Integer, Statement>> cache;

    public StatementService(LedgerReceiptRepository repo,
                            @Value("${misclab.statement.fetch:join}") String fetch,
                            @Value("${misclab.statement.cache-ttl:0s}") Duration cacheTtl) {
        this.repo = repo;
        this.joinFetch = !"naive".equalsIgnoreCase(fetch);
        this.cache = cacheTtl.isZero() ? null
                : Caffeine.newBuilder().expireAfterWrite(cacheTtl).maximumSize(10_000).build();
    }

    @Transactional(readOnly = true)
    public Statement statement(long payerId, int limit) {
        int n = Math.max(1, Math.min(limit, MAX_LIMIT));
        if (cache == null) return load(payerId, n);
        Map<Integer, Statement> byLimit = cache.get(payerId, k -> new ConcurrentHashMap<>());
        Statement hit = byLimit.get(n);
        if (hit != null) { hits.increment(); return hit; }
        misses.increment();
        Statement loaded = load(payerId, n);
        byLimit.put(n, loaded);   // if a write invalidated this payer meanwhile, byLimit is already detached: harmless
        return loaded;
    }

    private Statement load(long payerId, int n) {
        List<LedgerReceipt> receipts;
        if (joinFetch) {
            List<Long> ids = repo.findLatestIds(payerId, PageRequest.of(0, n));                       // query 1
            if (ids.isEmpty()) return toStatement(payerId, List.of());
            Map<Long, LedgerReceipt> byId = repo.findWithLines(ids).stream()                             // query 2
                    .collect(Collectors.toMap(LedgerReceipt::getId, Function.identity()));
            receipts = ids.stream().map(byId::get).toList();                                             // keep newest-first order
        } else {
            receipts = repo.findByPayerIdOrderByCreatedAtDescIdDesc(payerId, PageRequest.of(0, n));    // query 1 ...
        }
        return toStatement(payerId, receipts);                                                           // ... +N here if naive
    }

    private static Statement toStatement(long payerId, List<LedgerReceipt> receipts) {
        Map<String, Long> totals = new TreeMap<>();
        List<Statement.Item> items = receipts.stream().map(r -> {
            totals.merge(r.getCurrency(), r.getAmountMinor(), Long::sum);
            List<Statement.Line> lines = r.getLines().stream()                                           // lazy load point
                    .map(l -> new Statement.Line(l.getDescription(), l.getAmountMinor())).toList();
            return new Statement.Item(r.getId(), r.getMerchant(), r.getAmountMinor(), r.getCurrency(), r.getCreatedAt(), lines);
        }).toList();
        return new Statement(payerId, items.size(), totals, items);
    }

    /** Adds a receipt and evicts that payer's cached statements, so a reader never sees a stale statement after a write. */
    @Transactional
    public long addReceipt(long payerId, String merchant, String currency, List<Statement.Line> lines) {
        LedgerReceipt r = new LedgerReceipt(payerId, merchant, currency, Instant.now());
        lines.forEach(l -> r.addLine(l.description(), l.amountMinor()));
        long id = repo.save(r).getId();
        if (cache != null) evictAfterCommit(payerId);
        return id;
    }

    /**
     * Evict only once the new row is committed. Evicting inside the transaction (first draft) leaves a window where a
     * concurrent reader misses, reloads the OLD rows (the insert is not visible yet) and caches them for the whole TTL.
     */
    private void evictAfterCommit(long payerId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cache.invalidate(payerId); }
            });
        } else {
            cache.invalidate(payerId);
        }
    }

    /** Cache hit ratio for the perf report (0 when caching is off). */
    public double cacheHitRate() {
        long h = hits.sum(), m = misses.sum();
        return h + m == 0 ? 0 : (double) h / (h + m);
    }

    private final LongAdder hits = new LongAdder(), misses = new LongAdder();

    boolean usesJoinFetch() { return joinFetch; }
}
