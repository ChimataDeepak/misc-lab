package com.deepak.misclab;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Unit-level (no Docker): real Spring AOP + Resilience4j around FxClient, WireMock as the FX provider. */
@SpringBootTest(properties = {"misclab.aws.init-resources=false", "misclab.poller.enabled=false", "fx.read-timeout=300ms"})
class FxClientTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(
            com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) { r.add("fx.base-url", wm::baseUrl); }

    @Autowired FxClient fx;
    @Autowired CircuitBreakerRegistry cbRegistry;

    @BeforeEach
    void reset() { cbRegistry.circuitBreaker("fx").reset(); }

    @Test
    void returnsRateFromProvider() {
        wm.stubFor(get(urlPathEqualTo("/rates")).withQueryParam("from", equalTo("INR"))
                .willReturn(okJson("{\"rate\": 0.012}")));
        assertThat(fx.rateToUsd("INR")).isEqualByComparingTo("0.012");
    }

    @Test
    void retriesThenFallsBackOnServerError() {
        wm.stubFor(get(urlPathEqualTo("/rates")).willReturn(serverError()));
        assertThat(fx.rateToUsd("INR")).isEqualByComparingTo("0.0119");
        wm.verify(2, getRequestedFor(urlPathEqualTo("/rates")));  // max-attempts: 2
    }

    @Test
    void timeoutTriggersFallback() {
        wm.stubFor(get(urlPathEqualTo("/rates")).willReturn(okJson("{\"rate\": 0.012}").withFixedDelay(1000)));
        assertThat(fx.rateToUsd("INR")).isEqualByComparingTo("0.0119");
    }

    @Test
    void circuitOpensAndStopsCallingProvider() {
        wm.stubFor(get(urlPathEqualTo("/rates")).willReturn(serverError()));
        fx.rateToUsd("INR"); fx.rateToUsd("INR");   // 4 failed calls -> window full, 100% failures
        assertThat(cbRegistry.circuitBreaker("fx").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        wm.resetRequests();
        assertThat(fx.rateToUsd("INR")).isEqualByComparingTo("0.0119");
        wm.verify(0, getRequestedFor(urlPathEqualTo("/rates")));  // short-circuited, provider untouched
    }
}
