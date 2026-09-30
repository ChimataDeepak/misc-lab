package com.deepak.misclab.payments;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;

/** Remembers accepted webhook event ids for the replay check. */
public interface SeenEventStore {
    /** Records the id; returns false if it was already seen inside the window (= a replay). Forgets ids older than the window. */
    boolean markSeen(String eventId, long signedAt, long nowSeconds, long windowSeconds);

    /** Per-process memory: fine for a merchant-side test helper, lost on restart. */
    static SeenEventStore inMemory() {
        Map<String, Long> seen = new ConcurrentHashMap<>();
        return (id, t, now, window) -> {
            seen.values().removeIf(ts -> now - ts > window);
            return seen.putIfAbsent(id, t) == null;
        };
    }

    /** PostgreSQL: the primary key on event_id makes "first one wins" atomic across threads, instances and restarts. */
    static SeenEventStore jdbc(JdbcTemplate jdbc) {
        return (id, t, now, window) -> {
            jdbc.update("DELETE FROM webhook_received_events WHERE signed_at < ?", now - window);
            return jdbc.update("INSERT INTO webhook_received_events (event_id, signed_at) VALUES (?, ?) ON CONFLICT DO NOTHING", id, t) == 1;
        };
    }
}
