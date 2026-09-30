package com.deepak.misclab.payments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Sends a signed JSON event for each payment state change. Every event is kept in an in-memory outbox (so tests and
 * a GET endpoint can show what was sent); if a target URL is configured it is also POSTed there.
 * A delivery failure is logged, never thrown: the payment already happened, the webhook is a notification.
 * (Production next step: persist the outbox in the same DB transaction and retry with backoff.)
 */
public class WebhookPublisher {
    private static final Logger log = LoggerFactory.getLogger(WebhookPublisher.class);

    public record Event(String id, String type, PaymentIntent data) {}
    public record Sent(Event event, String body, String signatureHeader) {}

    private final String secret;
    private final String targetUrl;
    private final Clock clock;
    private final ObjectMapper json;
    private final RestClient http = RestClient.create();
    private final List<Sent> outbox = new CopyOnWriteArrayList<>();

    public WebhookPublisher(String secret, String targetUrl, Clock clock, ObjectMapper json) {
        this.secret = secret; this.targetUrl = targetUrl; this.clock = clock; this.json = json;
    }

    public Sent publish(String type, PaymentIntent pi) {
        Event event = new Event("evt_" + UUID.randomUUID(), type, pi);
        String body;
        try { body = json.writeValueAsString(event); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        String header = WebhookSigner.header(secret, clock.instant().getEpochSecond(), body);
        Sent sent = new Sent(event, body, header);
        outbox.add(sent);
        if (targetUrl != null && !targetUrl.isBlank()) {
            try {
                http.post().uri(targetUrl).contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.HEADER, header).header("Misclab-Event-Id", event.id())
                        .body(body).retrieve().toBodilessEntity();
            } catch (RuntimeException e) {
                log.warn("webhook delivery failed event={} type={}: {}", event.id(), type, e.getMessage());
            }
        }
        return sent;
    }

    public List<Sent> outbox() { return List.copyOf(outbox); }
}
