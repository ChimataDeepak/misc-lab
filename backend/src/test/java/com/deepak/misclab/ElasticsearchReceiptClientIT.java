package com.deepak.misclab;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The WireMock unit test only proves the requests we send; this one proves real Elasticsearch 8 behaviour:
 * without ?refresh=wait_for a search right after indexing returns nothing (near-real-time refresh), and the match
 * query is analysed (case-insensitive) and safe for payers containing quotes.
 */
@Testcontainers
class ElasticsearchReceiptClientIT {

    @Container
    static ElasticsearchContainer es = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.3"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    @Test
    void indexThenSearchAgainstRealElasticsearch() {
        var client = new ElasticsearchReceiptClient(
                HostPortReady.awaitHttp("http://" + es.getHttpHostAddress(), "/"), new ObjectMapper().findAndRegisterModules());
        Instant t = Instant.parse("2026-09-30T00:00:00Z");
        client.index(new Receipt("r1", "Deepak", new BigDecimal("10"), "USD", new BigDecimal("10.00"), t));
        client.index(new Receipt("r2", "priya", new BigDecimal("5"), "USD", new BigDecimal("5.00"), t));
        client.index(new Receipt("r3", "o\"brien \\ x", new BigDecimal("1"), "USD", new BigDecimal("1.00"), t));

        assertThat(client.searchPayer("deepak")).containsExactly("r1");   // standard analyzer lowercases
        assertThat(client.searchPayer("priya")).containsExactly("r2");
        assertThat(client.searchPayer("o\"brien \\ x")).contains("r3");  // quotes/backslash don't break the query JSON
        assertThat(client.searchPayer("nobody")).isEmpty();
    }
}
