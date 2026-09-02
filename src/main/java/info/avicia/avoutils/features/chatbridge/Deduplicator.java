package info.avicia.avoutils.features.chatbridge;

import java.util.HashMap;
import java.util.Map;

/**
 * Thread-safe, time-windowed deduplication helper.
 * Tracks every key seen within the window, so
 * interleaved duplicate deliveries are still caught.
 */
public class Deduplicator {
    private final long windowMs;
    private final Map<String, Long> lastSeen = new HashMap<>();

    public Deduplicator(long windowMs) {
        this.windowMs = windowMs;
    }

    public synchronized boolean isDuplicate(String key) {
        long now = System.currentTimeMillis();
        lastSeen.entrySet().removeIf(entry -> (now - entry.getValue()) >= windowMs);
        Long previous = lastSeen.get(key);
        lastSeen.put(key, now);
        return previous != null;
    }
}
