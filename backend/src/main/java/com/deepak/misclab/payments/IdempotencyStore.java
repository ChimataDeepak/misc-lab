package com.deepak.misclab.payments;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Idempotency-Key handling, the way Stripe-style APIs do it: the first request with a key runs and its result is
 * stored; a retry with the same key and same body gets the stored result (no double charge); the same key with a
 * different body is a client bug and is rejected. Keys are scoped by operation ("create", "capture:pi_1", ...).
 * In-memory for the lab; in a real service this is a DB table with a unique constraint and a TTL.
 */
public class IdempotencyStore {
    private record Entry(String fingerprint, Object result) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T execute(String scope, String key, String fingerprint, Supplier<T> action) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Idempotency-Key header is required");
        String k = scope + "|" + key;
        // computeIfAbsent runs the action at most once per key, even for concurrent retries.
        Entry e = entries.computeIfAbsent(k, x -> new Entry(fingerprint, action.get()));
        if (!e.fingerprint().equals(fingerprint)) throw new KeyReuseException(key);
        return (T) e.result();
    }

    public static class KeyReuseException extends RuntimeException {
        public KeyReuseException(String key) { super("Idempotency-Key '" + key + "' was already used with a different request"); }
    }
}
