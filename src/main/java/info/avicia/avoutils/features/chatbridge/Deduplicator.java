package info.avicia.avoutils.features.chatbridge;

/**
 * Thread-safe, time-windowed deduplication helper.
 * Drops a key if it was last seen within the configured window.
 */
public class Deduplicator {
    private final long windowMs;
    private String lastKey;
    private long lastTime;

    public Deduplicator(long windowMs) {
        this.windowMs = windowMs;
    }

    public synchronized boolean isDuplicate(String key) {
        long now = System.currentTimeMillis();
        if (key.equals(lastKey) && (now - lastTime) < windowMs) {
            return true;
        }
        lastKey = key;
        lastTime = now;
        return false;
    }
}
