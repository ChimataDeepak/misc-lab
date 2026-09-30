package com.deepak.misclab;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
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

/** Testcontainers-optional: WireMock stands in for Elasticsearch HTTP. */
class ElasticsearchReceiptClientTest {

    @RegisterExtension
    static WireMockExtension es = WireMockExtension.newInstance()
            .options(com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @Test
    void indexPutsDocThenSearchReturnsId() {
        es.stubFor(put(urlEqualTo("/receipts/_doc/r1")).willReturn(okJson("{\"result\":\"created\"}")));
        es.stubFor(post(urlEqualTo("/receipts/_search"))
                .willReturn(okJson("{\"hits\":{\"hits\":[{\"_id\":\"r1\"}]}}")));

        var client = new ElasticsearchReceiptClient(es.baseUrl(), new ObjectMapper().findAndRegisterModules());
        Receipt r = new Receipt("r1", "deepak", new BigDecimal("10"), "USD", new BigDecimal("10.00"),
                Instant.parse("2026-09-30T00:00:00Z"));
        client.index(r);
        assertThat(client.searchPayer("deepak")).containsExactly("r1");

        es.verify(putRequestedFor(urlEqualTo("/receipts/_doc/r1")).withHeader("Content-Type", equalTo("application/json")));
        es.verify(postRequestedFor(urlEqualTo("/receipts/_search")));
    }
}
