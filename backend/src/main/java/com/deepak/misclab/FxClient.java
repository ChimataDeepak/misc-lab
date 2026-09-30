package com.deepak.misclab;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Third-party FX API client. Layers (outer to inner): Retry -> CircuitBreaker -> HTTP call with timeouts.
 * The fallback sits on Retry (outermost), so retry still sees real failures, and an OPEN breaker
 * (CallNotPermittedException, ignored by retry) goes straight to the fallback rate.
 */
@Component
public class FxClient {
    private static final Logger log = LoggerFactory.getLogger(FxClient.class);
    record RateResponse(BigDecimal rate) {}

    private final RestClient rest;
    private final Timer timer;
    private final BigDecimal fallbackRate;

    public FxClient(RestClient.Builder builder, MeterRegistry registry,
                    @Value("${fx.base-url}") String baseUrl,
                    @Value("${fx.connect-timeout}") Duration connectTimeout,
                    @Value("${fx.read-timeout}") Duration readTimeout,
                    @Value("${fx.fallback-rate}") BigDecimal fallbackRate) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(connectTimeout);
        rf.setReadTimeout(readTimeout);   // the timeout: a slow provider can't hang our threads
        this.rest = builder.baseUrl(baseUrl).requestFactory(rf).build();
        this.timer = Timer.builder("fx_call").description("FX provider call latency").publishPercentileHistogram().register(registry);
        this.fallbackRate = fallbackRate;
    }

    @Retry(name = "fx", fallbackMethod = "fallbackRate")
    @CircuitBreaker(name = "fx")
    public BigDecimal rateToUsd(String from) {
        return timer.record(() -> rest.get().uri("/rates?from={f}&to=USD", from)
                .retrieve().body(RateResponse.class).rate());
    }

    BigDecimal fallbackRate(String from, Throwable t) {
        log.warn("FX provider unavailable ({}), using fallback rate {}", t.getClass().getSimpleName(), fallbackRate);
        return fallbackRate;
    }
}
