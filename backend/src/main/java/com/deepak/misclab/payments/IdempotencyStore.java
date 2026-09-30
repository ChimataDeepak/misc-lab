package com.deepak.misclab.payments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Idempotency-Key handling, Stripe-style, backed by the {@code idempotency_keys} table whose primary key
 * (scope, idem_key) is the real guarantee.
 *
 * <p>Must run inside the caller's transaction, together with the action:
 * <ol>
 *   <li>{@code INSERT ... ON CONFLICT DO NOTHING} claims the key. If another transaction holds an uncommitted row for
 *       the same key, PostgreSQL makes this statement <b>wait</b> for that transaction to finish.</li>
 *   <li>1 row inserted: we own the key, run the action and store its JSON result in the same transaction. If the
 *       action throws, the whole transaction (key row included) rolls back and the key is free again.</li>
 *   <li>0 rows: someone already committed this key. Same fingerprint: return the stored result (no second payment,
 *       no second webhook). Different fingerprint: the client reused a key for a different request, 422.</li>
 * </ol>
 * Scopes: "create", "capture:pi_1", "refund:pi_1". (Production next step: a TTL job deleting keys older than 24 h.)
 */
public class IdempotencyStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public IdempotencyStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public <T> T execute(String scope, String key, String fingerprint, Class<T> type, Supplier<T> action) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Idempotency-Key header is required");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("IdempotencyStore.execute must run inside a transaction");
        }
        int claimed = jdbc.update("INSERT INTO idempotency_keys (scope, idem_key, fingerprint) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                scope, key, fingerprint);
        if (claimed == 1) {
            T result = action.get();
            jdbc.update("UPDATE idempotency_keys SET response_json = ? WHERE scope = ? AND idem_key = ?", toJson(result), scope, key);
            return result;
        }
        Map<String, Object> row = jdbc.queryForMap("SELECT fingerprint, response_json FROM idempotency_keys WHERE scope = ? AND idem_key = ?",
                scope, key);
        if (!fingerprint.equals(row.get("fingerprint"))) throw new KeyReuseException(key);
        String stored = (String) row.get("response_json");
        if (stored == null) throw new IllegalStateException("idempotency key " + key + " committed without a result");
        try { return json.readValue(stored, type); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private String toJson(Object o) {
        try { return json.writeValueAsString(o); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    public static class KeyReuseException extends RuntimeException {
        public KeyReuseException(String key) { super("Idempotency-Key '" + key + "' was already used with a different request"); }
    }
}
