package com.deepak.misclab.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.deepak.misclab.FxClient;
import com.deepak.misclab.Receipt;
import com.deepak.misclab.ReceiptService;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Cache-aside against a real Redis 7 (Testcontainers). The sources of truth are mocks so we can count real loads. */
@SpringBootTest(properties = {"misclab.aws.init-resources=false", "misclab.poller.enabled=false",
        "misclab.cache.enabled=true", "spring.cache.type=redis", "misclab.cache.receipt-ttl=1h", "misclab.cache.fx-ttl=10m"})
@Testcontainers
class ReceiptCacheIT {
    @Container @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @MockitoBean ReceiptService receipts;
    @MockitoBean FxClient fx;
    @Autowired CachedLookups cached;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired MeterRegistry meters;

    static Receipt receipt(String id, String payer) {
        return new Receipt(id, payer, new BigDecimal("1000"), "INR", new BigDecimal("12.00"), Instant.parse("2026-09-30T10:00:00Z"));
    }

    double gets(String cache, String result) {
        return meters.get("cache.gets").tag("cache", cache).tag("result", result).functionCounter().count();
    }

    @Test void secondReceiptLookupIsACacheHitAndUpdateEvicts() {
        when(receipts.get("r-1")).thenReturn(receipt("r-1", "deepak"));
        double hits0 = gets("receipts", "hit"), misses0 = gets("receipts", "miss");

        assertThat(cached.receipt("r-1").payer()).isEqualTo("deepak");   // miss -> loads from DynamoDB (mock)
        assertThat(cached.receipt("r-1").payer()).isEqualTo("deepak");   // hit  -> served by Redis
        verify(receipts, times(1)).get("r-1");
        assertThat(gets("receipts", "miss") - misses0).isEqualTo(1);
        assertThat(gets("receipts", "hit") - hits0).isEqualTo(1);

        Long ttl = redisTemplate.getExpire("receipts::r-1", TimeUnit.SECONDS);
        assertThat(ttl).isBetween(3500L, 3600L);                        // entry has the configured 1 h TTL
        assertThat(redisTemplate.opsForValue().get("receipts::r-1")).contains("\"payer\":\"deepak\"");  // JSON, not Java-serialized

        when(receipts.updatePayer("r-1", "deepak ch")).thenReturn(receipt("r-1", "deepak ch"));
        when(receipts.get("r-1")).thenReturn(receipt("r-1", "deepak ch"));
        cached.updatePayer("r-1", "deepak ch");                          // write to source, then evict
        assertThat(redisTemplate.hasKey("receipts::r-1")).isFalse();
        assertThat(cached.receipt("r-1").payer()).isEqualTo("deepak ch"); // fresh value, not the stale cached one
        verify(receipts, times(2)).get("r-1");
    }

    @Test void unknownReceiptIsNotCached() {
        when(receipts.get(anyString())).thenReturn(null);
        assertThat(cached.receipt("missing")).isNull();
        assertThat(cached.receipt("missing")).isNull();
        verify(receipts, times(2)).get("missing");
        assertThat(redisTemplate.hasKey("receipts::missing")).isFalse();
    }

    @Test void fxRateIsCachedPerCurrencyAndCanBeEvicted() {
        when(fx.rateToUsd("EUR")).thenReturn(new BigDecimal("1.08"));
        assertThat(cached.fxRate("eur")).isEqualByComparingTo("1.08");
        assertThat(cached.fxRate("EUR")).isEqualByComparingTo("1.08");   // same key after upper-casing
        verify(fx, times(1)).rateToUsd("EUR");
        assertThat(redisTemplate.getExpire("fxRates::EUR", TimeUnit.SECONDS)).isBetween(590L, 600L);

        cached.evictFxRate("EUR");
        cached.fxRate("EUR");
        verify(fx, times(2)).rateToUsd("EUR");
    }
}
