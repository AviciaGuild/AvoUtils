package info.avicia.avoutils.features.chatbridge;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidDetectorTest {

    @Test
    void detectsSinglePlayerRaidWithoutRewards() {
        String cleaned = "Steve finished Nest of the Grootslangs and claimed +100000m Guild Experience";
        RaidDetector.RaidResult result = RaidDetector.tryDetect(cleaned, Text.literal(""));

        assertNotNull(result);
        assertEquals(0, result.emeralds());
        assertEquals(0, result.aspects());
        assertTrue(result.formattedMessage().contains("**Steve** finished **Nest of the Grootslangs**"));
        assertTrue(result.formattedMessage().contains("+100.000m Guild XP"));
    }

    @Test
    void detectsMultiPlayerRaidWithAspectsAndSr() {
        String cleaned = "Steve, Alex and Bob finished NOL and claimed 12x Aspects, and +123456m Guild Experience, and +3 Seasonal Rating";
        RaidDetector.RaidResult result = RaidDetector.tryDetect(cleaned, Text.literal(""));

        assertNotNull(result);
        assertEquals(12, result.aspects());
        assertEquals(0, result.emeralds());
        assertTrue(result.formattedMessage().contains("**Steve, Alex, and Bob** finished **NOL**"));
        assertTrue(result.formattedMessage().contains("12x Aspects"));
        assertTrue(result.formattedMessage().contains("+123.456m Guild XP"));
        assertTrue(result.formattedMessage().contains("+3 SR"));
    }

    @Test
    void detectsMixedRewards() {
        String cleaned = "Steve and Alex finished NOL and claimed 2x Aspects, 3x Emeralds and +100000m Guild Experience";
        RaidDetector.RaidResult result = RaidDetector.tryDetect(cleaned, Text.literal(""));

        assertNotNull(result);
        assertEquals(2, result.aspects());
        assertEquals(3, result.emeralds());
    }

    @Test
    void returnsNullForTooManyNames() {
        String cleaned = "A, B, C, D, E finished NOL and claimed +100000m Guild Experience";
        assertNull(RaidDetector.tryDetect(cleaned, Text.literal("")));
    }

    @Test
    void returnsNullWhenNamesContainColon() {
        String cleaned = "Steve: finished NOL and claimed +100000m Guild Experience";
        assertNull(RaidDetector.tryDetect(cleaned, Text.literal("")));
    }

    @Test
    void returnsNullForNonRaidText() {
        assertNull(RaidDetector.tryDetect("Steve says hi", Text.literal("")));
    }
}
