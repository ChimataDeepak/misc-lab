package com.deepak.misclab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch REST client (index + match query). Point {@code misclab.es.base-url} at a real
 * cluster, Testcontainers Elasticsearch, or a WireMock of {@code PUT /receipts/_doc/{id}} and
 * {@code POST /receipts/_search}. Not an ELK cluster you operate.
 */
public class ElasticsearchReceiptClient {
    private final HttpClient http;
    private final String baseUrl;
    private final ObjectMapper json;

    public ElasticsearchReceiptClient(String baseUrl, ObjectMapper json) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.json = json;
    }

    public void index(Receipt receipt) {
        try {
            String body = json.writeValueAsString(receipt);
            var req = HttpRequest.newBuilder(URI.create(baseUrl + "/receipts/_doc/" + receipt.receiptId() + "?refresh=wait_for"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(3))
                    .build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                throw new IllegalStateException("ES index HTTP " + res.statusCode() + ": " + res.body());
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public List<String> searchPayer(String payer) {
        try {
            // Build the query with Jackson, not string concat: a payer containing quotes or backslashes can't break the JSON.
            var query = json.createObjectNode();
            query.putObject("query").putObject("match").put("payer", payer);
            String q = json.writeValueAsString(query);
            var req = HttpRequest.newBuilder(URI.create(baseUrl + "/receipts/_search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(q, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(3))
                    .build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                throw new IllegalStateException("ES search HTTP " + res.statusCode() + ": " + res.body());
            }
            JsonNode hits = json.readTree(res.body()).path("hits").path("hits");
            List<String> ids = new ArrayList<>();
            for (JsonNode hit : hits) {
                ids.add(hit.path("_id").asText());
            }
            return ids;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
