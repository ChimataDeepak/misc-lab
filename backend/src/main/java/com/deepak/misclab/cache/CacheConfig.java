package com.deepak.misclab.cache;

import com.deepak.misclab.Receipt;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Redis cache-aside, on with {@code misclab.cache.enabled=true} (+ {@code spring.cache.type=redis}).
 * Each cache has its own TTL and a typed JSON serializer (readable in redis-cli, no Java serialization).
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "misclab.cache.enabled", havingValue = "true")
public class CacheConfig {
    public static final String FX_RATES = "fxRates";
    public static final String RECEIPTS = "receipts";

    @Bean
    RedisCacheManagerBuilderCustomizer receiptCaches(ObjectMapper json,
                                                     @Value("${misclab.cache.fx-ttl}") Duration fxTtl,
                                                     @Value("${misclab.cache.receipt-ttl}") Duration receiptTtl) {
        ObjectMapper mapper = json.copy().findAndRegisterModules();
        return b -> b
                .withCacheConfiguration(FX_RATES, RedisCacheConfiguration.defaultCacheConfig().entryTtl(fxTtl)
                        .disableCachingNullValues()
                        .serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, BigDecimal.class))))
                .withCacheConfiguration(RECEIPTS, RedisCacheConfiguration.defaultCacheConfig().entryTtl(receiptTtl)
                        .disableCachingNullValues()
                        .serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, Receipt.class))));
    }
}
