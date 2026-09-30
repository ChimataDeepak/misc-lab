package com.deepak.misclab.payments;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Receiver side of {@link WebhookSigner}. Three checks, in this order:
 * 1. signature matches (constant-time compare, so timing doesn't leak how many bytes matched),
 * 2. timestamp is within the tolerance window (old captures can't be replayed next week),
 * 3. the event id hasn't been seen inside the window (an exact replay within 5 minutes is also rejected).
 */
public class WebhookVerifier {
    private final String secret;
    private final Duration tolerance;
    private final Clock clock;
    private final Map<String, Long> seenEventIds = new ConcurrentHashMap<>();

    public WebhookVerifier(String secret, Duration tolerance, Clock clock) {
        this.secret = secret; this.tolerance = tolerance; this.clock = clock;
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
        seenEventIds.values().removeIf(ts -> now - ts > tolerance.toSeconds()); // forget ids older than the window
        if (seenEventIds.putIfAbsent(eventId, t) != null) throw new Rejected("replayed event " + eventId);
    }

    public static class Rejected extends RuntimeException {
        public Rejected(String msg) { super(msg); }
    }
}
