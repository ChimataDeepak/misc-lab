package com.deepak.misclab;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LlmClientTest {

    @RegisterExtension
    static WireMockExtension llm = WireMockExtension.newInstance()
            .options(com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @Test
    void summarizePostsToChatCompletionsStub() {
        llm.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(okJson("{\"choices\":[{\"message\":{\"content\":\"r1 paid 10 USD\"}}]}")));

        var client = new LlmClient(llm.baseUrl(), "test-not-a-real-key", new ObjectMapper());
        Receipt r = new Receipt("r1", "deepak", new BigDecimal("10"), "USD", new BigDecimal("10.00"),
                Instant.parse("2026-09-30T00:00:00Z"));
        assertThat(client.summarizeReceipt(r)).isEqualTo("r1 paid 10 USD");
        llm.verify(postRequestedFor(urlEqualTo("/v1/chat/completions")).withRequestBody(containing("r1")));
    }
}
