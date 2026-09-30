package com.deepak.misclab.payments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

/**
 * Signed JSON event per payment state change, written to the {@code webhook_outbox} table in the <b>same transaction</b>
 * as the state change (so an event exists if and only if the change committed). If a target URL is configured, the
 * POST happens <b>after commit</b>: a rolled-back transition never notifies the merchant.
 * A delivery failure is logged, never thrown: the payment already happened, the webhook is a notification.
 * (Production next step: a relay that retries undelivered outbox rows with backoff; here delivery is best-effort once.)
 */
public class WebhookPublisher {
    private static final Logger log = LoggerFactory.getLogger(WebhookPublisher.class);

    public record Event(String id, String type, PaymentIntent data) {}
    public record Sent(String eventId, String type, String paymentId, String body, String signatureHeader) {}

    private static final RowMapper<Sent> ROW = (rs, i) -> new Sent(rs.getString("event_id"), rs.getString("type"),
            rs.getString("payment_id"), rs.getString("body"), rs.getString("signature"));

    private final String secret;
    private final String targetUrl;
    private final Clock clock;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final RestClient http = RestClient.create();

    public WebhookPublisher(String secret, String targetUrl, Clock clock, ObjectMapper json, JdbcTemplate jdbc) {
        this.secret = secret; this.targetUrl = targetUrl; this.clock = clock; this.json = json; this.jdbc = jdbc;
    }

    public Sent publish(String type, PaymentIntent pi) {
        Event event = new Event("evt_" + UUID.randomUUID(), type, pi);
        String body;
        try { body = json.writeValueAsString(event); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        String header = WebhookSigner.header(secret, clock.instant().getEpochSecond(), body);
        Sent sent = new Sent(event.id(), type, pi.id(), body, header);
        jdbc.update("INSERT INTO webhook_outbox (event_id, type, payment_id, body, signature) VALUES (?, ?, ?, ?, ?)",
                sent.eventId(), type, pi.id(), body, header);
        if (targetUrl != null && !targetUrl.isBlank()) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { deliver(sent); }
                });
            } else {
                deliver(sent);
            }
        }
        return sent;
    }

    private void deliver(Sent sent) {
        try {
            http.post().uri(targetUrl).contentType(MediaType.APPLICATION_JSON)
                    .header(WebhookSigner.HEADER, sent.signatureHeader()).header("Misclab-Event-Id", sent.eventId())
                    .body(sent.body()).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("webhook delivery failed event={} type={}: {}", sent.eventId(), sent.type(), e.getMessage());
        }
    }

    public List<Sent> outbox() { return jdbc.query("SELECT * FROM webhook_outbox ORDER BY seq", ROW); }

    public List<Sent> outboxFor(String paymentId) {
        return jdbc.query("SELECT * FROM webhook_outbox WHERE payment_id = ? ORDER BY seq", ROW, paymentId);
    }
}
