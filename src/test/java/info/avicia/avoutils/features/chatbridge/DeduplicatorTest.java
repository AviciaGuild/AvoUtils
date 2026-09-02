package info.avicia.avoutils.features.chatbridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeduplicatorTest {

    @Test
    void firstOccurrenceIsNotDuplicate() {
        Deduplicator deduplicator = new Deduplicator(1_000);
        assertFalse(deduplicator.isDuplicate("key-a"));
    }

    @Test
    void repeatWithinWindowIsDuplicate() {
        Deduplicator deduplicator = new Deduplicator(1_000);
        assertFalse(deduplicator.isDuplicate("key-a"));
        assertTrue(deduplicator.isDuplicate("key-a"));
        assertFalse(deduplicator.isDuplicate("key-b"));
    }

    @Test
    void entryExpiresAfterWindow() throws InterruptedException {
        Deduplicator deduplicator = new Deduplicator(30);
        assertFalse(deduplicator.isDuplicate("key-a"));
        Thread.sleep(100);
        assertFalse(deduplicator.isDuplicate("key-a"));
    }
}
