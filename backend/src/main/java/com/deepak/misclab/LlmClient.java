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

/**
 * OpenAI-compatible chat client against a local stub ({@code POST /v1/chat/completions}).
 * No real API key; tests use WireMock. Do not claim production LLM/RAG.
 */
public class LlmClient {
    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final ObjectMapper json;

    public LlmClient(String baseUrl, String apiKey, ObjectMapper json) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.json = json;
    }

    public String complete(String prompt) {
        try {
            String body = "{\"model\":\"stub\",\"messages\":[{\"role\":\"user\",\"content\":"
                    + json.writeValueAsString(prompt) + "}]}";
            var req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                throw new IllegalStateException("LLM HTTP " + res.statusCode() + ": " + res.body());
            }
            JsonNode root = json.readTree(res.body());
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public String summarizeReceipt(Receipt receipt) {
        return complete("Summarize receipt " + receipt.receiptId() + " payer " + receipt.payer()
                + " usd " + receipt.usdAmount());
    }
}
