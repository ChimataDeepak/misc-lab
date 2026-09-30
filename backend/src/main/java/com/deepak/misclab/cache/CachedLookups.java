package com.deepak.misclab.cache;

import com.deepak.misclab.FxClient;
import com.deepak.misclab.Receipt;
import com.deepak.misclab.ReceiptService;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Cache-aside in front of the two hot reads: FX rate per currency (external API, slow, rate-limited) and receipt by
 * id (DynamoDB). On a miss Spring calls the method and stores the result with the cache's TTL; on a hit the method
 * body never runs. Writes go to the source of truth first, then the entry is <b>evicted</b> (not updated), so the
 * next read reloads fresh data - avoids writing a stale value into the cache in a race between two writers.
 */
@Service
@ConditionalOnProperty(name = "misclab.cache.enabled", havingValue = "true")
public class CachedLookups {
    private final FxClient fx;
    private final ReceiptService receipts;

    public CachedLookups(FxClient fx, ReceiptService receipts) { this.fx = fx; this.receipts = receipts; }

    @Cacheable(cacheNames = CacheConfig.FX_RATES, key = "#currency.toUpperCase()")
    public BigDecimal fxRate(String currency) { return fx.rateToUsd(currency.toUpperCase()); }

    /** Unknown ids are not cached (null is not stored), so a receipt created later is found. */
    @Cacheable(cacheNames = CacheConfig.RECEIPTS, key = "#id", unless = "#result == null")
    public Receipt receipt(String id) { return receipts.get(id); }

    @CacheEvict(cacheNames = CacheConfig.RECEIPTS, key = "#id")
    public Receipt updatePayer(String id, String payer) { return receipts.updatePayer(id, payer); }

    /** Ops hook: FX provider published a correction, drop our copy now instead of waiting for the TTL. */
    @CacheEvict(cacheNames = CacheConfig.FX_RATES, key = "#currency.toUpperCase()")
    public void evictFxRate(String currency) { }
}
