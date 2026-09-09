package info.avicia.avoutils.features.chatbridge;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardDetectorTest {

    @Test
    void detectsEmeraldReward() {
        RewardDetector.RewardResult result =
                RewardDetector.tryDetect("Steve rewarded 1,000 Emeralds to Alex", Text.literal(""));

        assertNotNull(result);
        assertEquals(1000L, result.emeraldAmount());
        assertEquals("**Steve** rewarded **1,000 Emeralds** to **Alex**", result.formattedMessage());
    }

    @Test
    void detectsSingleAspect() {
        RewardDetector.RewardResult result =
                RewardDetector.tryDetect("Steve rewarded an Aspect to Alex", Text.literal(""));

        assertNotNull(result);
        assertEquals(1L, result.aspectAmount());
        assertEquals("**Steve** rewarded **1 Aspect** to **Alex**", result.formattedMessage());
    }

    @Test
    void detectsMultipleAspects() {
        RewardDetector.RewardResult result =
                RewardDetector.tryDetect("Steve rewarded 2 Aspects to Alex", Text.literal(""));

        assertNotNull(result);
        assertEquals(2L, result.aspectAmount());
        assertTrue(result.formattedMessage().contains("**2 Aspects**"));
    }

    @Test
    void detectsGuildTome() {
        RewardDetector.RewardResult result =
                RewardDetector.tryDetect("Steve rewarded a Guild Tome to Alex", Text.literal(""));

        assertNotNull(result);
        assertEquals(1L, result.tomeCount());
        assertEquals("**Steve** rewarded **1 Guild Tome** to **Alex**", result.formattedMessage());
    }

    @Test
    void escapesUnderscoresInNames() {
        RewardDetector.RewardResult result =
                RewardDetector.tryDetect("Steve_Bob rewarded 5 Emeralds to Alex", Text.literal(""));

        assertNotNull(result);
        assertEquals("**Steve\\_Bob** rewarded **5 Emeralds** to **Alex**", result.formattedMessage());
    }

    @Test
    void returnsNullForNonRewardText() {
        assertNull(RewardDetector.tryDetect("Steve says hi", Text.literal("")));
        assertNull(RewardDetector.tryDetect(null, Text.literal("")));
        assertNull(RewardDetector.tryDetect("Steve rewarded something to Alex", Text.literal("")));
    }

    @Test
    void detectsEmeraldRewardWithHoverText() {
        // Correctly mock the Wynncraft Text component where the sender has hover text but the recipient does not
        net.minecraft.text.MutableText message = net.minecraft.text.Text.empty()
                .append(info.avicia.avoutils.testutil.TextFixtures.hoverText("Steve", "'s real name is NotSteve"))
                .append(" rewarded 1,000 Emeralds to Alex");
                
        RewardDetector.RewardResult result =
                RewardDetector.tryDetect("Steve rewarded 1,000 Emeralds to Alex", message);

        org.junit.jupiter.api.Assertions.assertNotNull(result);
        org.junit.jupiter.api.Assertions.assertEquals(1000L, result.emeraldAmount());
        // Verify that the sender resolved to NotSteve, but the recipient correctly stayed Alex
        org.junit.jupiter.api.Assertions.assertEquals("**NotSteve** rewarded **1,000 Emeralds** to **Alex**", result.formattedMessage());
    }
}