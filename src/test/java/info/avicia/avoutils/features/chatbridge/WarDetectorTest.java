package info.avicia.avoutils.features.chatbridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarDetectorTest {

    @Test
    void formattedMessageIncludesDurationDpsAndWarrers() {
        WarDetector.WarResult result =
                new WarDetector.WarResult("Captured", "NOL", "❤ 1.0M · ☠ 100-200 (2x)", "Steve, Alex", 3661, 1234);

        String message = result.formattedMessage();

        assertTrue(message.contains("**Captured: NOL**"));
        assertTrue(message.contains("⏱ 1h 1m"));
        assertTrue(message.contains("⚔ 1.2k dps"));
        assertTrue(message.contains("👥 Steve, Alex"));
    }

    @Test
    void formattedMessageOmitsDpsAndWarrersWhenAbsent() {
        WarDetector.WarResult result =
                new WarDetector.WarResult("Failed", "NOL", "❤ 1.0M · ☠ 0-0", "", 90, 0);

        String message = result.formattedMessage();

        assertTrue(message.contains("**Failed: NOL**"));
        assertTrue(message.contains("⏱ 1m 30s"));
        assertFalse(message.contains("⚔"));
        assertFalse(message.contains("👥"));
    }
}
