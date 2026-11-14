package info.avicia.avoutils.core.chat;

import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RaidDetectorTest {

    @Test
    void detectsSinglePlayerRaidWithoutRewards() {
        String cleaned = "Steve finished Nest of the Grootslangs and claimed +100000m Guild Experience";
        RaidDetector.RaidResult result = RaidDetector.tryDetect(cleaned, Component.literal(""));

        assertNotNull(result);
        assertEquals(List.of("Steve"), result.players());
        assertEquals("Nest of the Grootslangs", result.raidName());
        assertEquals(0, result.emeralds());
        assertEquals(0, result.aspects());
        assertEquals(100.0, result.guildXp(), 1e-6);
        assertEquals(0, result.seasonalRating());
    }

    @Test
    void detectsMultiPlayerRaidWithAspectsAndSr() {
        String cleaned = "Steve_1, Alex and Bob finished NOL and claimed 12x Aspects, and +123456m Guild Experience, and +3 Seasonal Rating";
        RaidDetector.RaidResult result = RaidDetector.tryDetect(cleaned, Component.literal(""));

        assertNotNull(result);
        assertEquals(List.of("Steve_1", "Alex", "Bob"), result.players());
        assertEquals("NOL", result.raidName());
        assertEquals(12, result.aspects());
        assertEquals(0, result.emeralds());
        assertEquals(123.456, result.guildXp(), 1e-6);
        assertEquals(3, result.seasonalRating());
    }

    @Test
    void detectsMixedRewards() {
        String cleaned = "Steve and Alex finished NOL and claimed 2x Aspects, 3x Emeralds and +100000m Guild Experience";
        RaidDetector.RaidResult result = RaidDetector.tryDetect(cleaned, Component.literal(""));

        assertNotNull(result);
        assertEquals(List.of("Steve", "Alex"), result.players());
        assertEquals(2, result.aspects());
        assertEquals(3, result.emeralds());
    }

    @Test
    void returnsNullForTooManyNames() {
        String cleaned = "A, B, C, D, E finished NOL and claimed +100000m Guild Experience";
        assertNull(RaidDetector.tryDetect(cleaned, Component.literal("")));
    }

    @Test
    void returnsNullWhenNoNamesCanBeResolved() {
        String cleaned = "ab and cd finished NOL and claimed +100000m Guild Experience";
        assertNull(RaidDetector.tryDetect(cleaned, Component.literal("")));
    }

    @Test
    void returnsNullForNullCleaned() {
        assertNull(RaidDetector.tryDetect(null, Component.literal("")));
    }

    @Test
    void returnsNullWhenNamesContainColon() {
        String cleaned = "Steve: finished NOL and claimed +100000m Guild Experience";
        assertNull(RaidDetector.tryDetect(cleaned, Component.literal("")));
    }

    @Test
    void returnsNullForNonRaidText() {
        assertNull(RaidDetector.tryDetect("Steve says hi", Component.literal("")));
    }
}
