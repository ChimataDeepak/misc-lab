package com.deepak.misclab.payments;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;

/**
 * Receiver side of {@link WebhookSigner}. Three checks, in this order:
 * 1. signature matches (constant-time compare, so timing doesn't leak how many bytes matched),
 * 2. timestamp is within the tolerance window (old captures can't be replayed next week),
 * 3. the event id hasn't been seen inside the window (an exact replay within 5 minutes is also rejected).
 * Seen ids live in a {@link SeenEventStore}: PostgreSQL in the app (survives restarts), memory in tests/helpers.
 */
public class WebhookVerifier {
    private final String secret;
    private final Duration tolerance;
    private final Clock clock;
    private final SeenEventStore seen;

    public WebhookVerifier(String secret, Duration tolerance, Clock clock) {
        this(secret, tolerance, clock, SeenEventStore.inMemory());
    }

    public WebhookVerifier(String secret, Duration tolerance, Clock clock, SeenEventStore seen) {
        this.secret = secret; this.tolerance = tolerance; this.clock = clock; this.seen = seen;
    }

    public void verify(String header, String body, String eventId) {
        long t = -1; String v1 = null;
        if (header != null) {
            for (String part : header.split(",")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length != 2) continue;
                if (kv[0].equals("t")) { try { t = Long.parseLong(kv[1]); } catch (NumberFormatException ignored) { } }
                if (kv[0].equals("v1")) v1 = kv[1];
            }
        }
        if (t < 0 || v1 == null) throw new Rejected("malformed signature header");
        String expected = WebhookSigner.hmacHex(secret, t + "." + body);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8))) {
            throw new Rejected("signature mismatch");
        }
        long now = clock.instant().getEpochSecond();
        if (Math.abs(now - t) > tolerance.toSeconds()) throw new Rejected("timestamp outside tolerance");
        if (eventId == null || eventId.isBlank()) throw new Rejected("missing event id");
        if (!seen.markSeen(eventId, t, now, tolerance.toSeconds())) throw new Rejected("replayed event " + eventId);
    }

    public static class Rejected extends RuntimeException {
        public Rejected(String msg) { super(msg); }
    }
}
