package com.deepak.misclab.payments;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Whole payment lifecycle through the real Spring MVC stack; a WireMock server plays the merchant receiving webhooks. */
@SpringBootTest(properties = {"misclab.aws.init-resources=false", "misclab.poller.enabled=false"})
@AutoConfigureMockMvc
class PaymentApiIntegrationTest {

    @RegisterExtension
    static WireMockExtension merchant = WireMockExtension.newInstance().options(WireMockConfiguration.wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) { r.add("misclab.payments.webhook-url", () -> merchant.baseUrl() + "/hooks"); }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void lifecycleWithIdempotencyAndSignedWebhooks() throws Exception {
        merchant.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/hooks").willReturn(ok()));
        String create = "{\"receiptId\":\"r-42\",\"amountMinor\":100000,\"currency\":\"INR\"}";

        String body = mvc.perform(post("/api/payments").header("Idempotency-Key", "create-42").contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.amount.minor").value(100000)).andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();

        // retry of the same create -> same payment, 201, no duplicate
        mvc.perform(post("/api/payments").header("Idempotency-Key", "create-42").contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(jsonPath("$.id").value(id));
        // same key, different body -> 422
        mvc.perform(post("/api/payments").header("Idempotency-Key", "create-42").contentType(MediaType.APPLICATION_JSON)
                .content(create.replace("100000", "1"))).andExpect(status().isUnprocessableEntity());
        // missing key -> 400
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(create)).andExpect(status().isBadRequest());
        // capture before authorize -> 409
        mvc.perform(post("/api/payments/" + id + "/capture").header("Idempotency-Key", "cap")).andExpect(status().isConflict());

        mvc.perform(post("/api/payments/" + id + "/authorize")).andExpect(jsonPath("$.status").value("AUTHORIZED"));
        mvc.perform(post("/api/payments/" + id + "/capture").header("Idempotency-Key", "cap")).andExpect(jsonPath("$.status").value("CAPTURED"));
        mvc.perform(post("/api/payments/" + id + "/refund").header("Idempotency-Key", "ref-1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountMinor\":25000}")).andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"));
        mvc.perform(post("/api/payments/" + id + "/refund").header("Idempotency-Key", "ref-2").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountMinor\":999999}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/payments/" + id + "/void")).andExpect(status().isConflict());
        mvc.perform(get("/api/payments/" + id)).andExpect(jsonPath("$.refunded.minor").value(25000));
        mvc.perform(get("/api/payments/pi_missing")).andExpect(status().isNotFound());

        // merchant got 4 signed webhooks; each one verifies with the shared secret
        List<LoggedRequest> hooks = merchant.findAll(postRequestedFor(urlEqualTo("/hooks")));
        assertThat(hooks).hasSize(4);
        WebhookVerifier merchantSide = new WebhookVerifier("whsec_misclab_lab_only", Duration.ofMinutes(5), Clock.systemUTC());
        for (LoggedRequest h : hooks) {
            merchantSide.verify(h.getHeader(WebhookSigner.HEADER), h.getBodyAsString(), h.getHeader("Misclab-Event-Id"));
        }
        assertThat(hooks).extracting(h -> json.readTree(h.getBodyAsString()).get("type").asText()).containsExactly(
                "payment_intent.created", "payment_intent.authorized", "payment_intent.captured", "payment_intent.refunded");

        // the receiver endpoint accepts a genuine event once, rejects the replay
        LoggedRequest first = hooks.get(0);
        mvc.perform(post("/api/payments/webhooks/receive").header(WebhookSigner.HEADER, first.getHeader(WebhookSigner.HEADER))
                .header("Misclab-Event-Id", first.getHeader("Misclab-Event-Id")).content(first.getBodyAsString()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/payments/webhooks/receive").header(WebhookSigner.HEADER, first.getHeader(WebhookSigner.HEADER))
                .header("Misclab-Event-Id", first.getHeader("Misclab-Event-Id")).content(first.getBodyAsString()))
                .andExpect(status().isUnauthorized());
    }
}
