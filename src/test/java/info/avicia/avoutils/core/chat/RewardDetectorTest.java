package info.avicia.avoutils.core.chat;

import info.avicia.avoutils.testutil.TextFixtures;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RewardDetectorTest {

    @Test
    void detectsEmeraldReward() {
        RewardDetector.RewardResult result =
                RewardDetector.tryDetect("Steve rewarded 1,000 Emeralds to Alex", Component.literal(""));

        assertNotNull(result);
        assertEquals("Steve", result.sender());
        assertEquals("Alex", result.recipient());
        assertEquals(1000L, result.emeraldAmount());
        assertEquals(0L, result.aspectAmount());
        assertEquals(0L, result.tomeCount());
    }

    @Test
    void detectsSingleAspect() {
        RewardDetector.RewardResult result =
                RewardDetector.tryDetect("Steve rewarded an Aspect to Alex", Component.literal(""));

        assertNotNull(result);
        assertEquals(1L, result.aspectAmount());
    }

    @Test
    void detectsMultipleAspects() {
        RewardDetector.RewardResult result =
                RewardDetector.tryDetect("Steve rewarded 2 Aspects to Alex", Component.literal(""));

        assertNotNull(result);
        assertEquals(2L, result.aspectAmount());
    }

    @Test
    void detectsGuildTome() {
        RewardDetector.RewardResult result =
                RewardDetector.tryDetect("Steve rewarded a Guild Tome to Alex", Component.literal(""));

        assertNotNull(result);
        assertEquals(1L, result.tomeCount());
    }

    @Test
    void keepsTheNamesUnescapedInTheResult() {
        RewardDetector.RewardResult result =
                RewardDetector.tryDetect("Steve_Bob rewarded a Guild Tome to Alex_2", Component.literal(""));

        assertNotNull(result);
        assertEquals("Steve_Bob", result.sender());
        assertEquals("Alex_2", result.recipient());
    }

    @Test
    void returnsNullForNonRewardText() {
        assertNull(RewardDetector.tryDetect("Steve says hi", Component.literal("")));
        assertNull(RewardDetector.tryDetect(null, Component.literal("")));
        assertNull(RewardDetector.tryDetect("Steve rewarded something to Alex", Component.literal("")));
    }

    @Test
    void ignoresChatLinesThatMentionARewardGrant() {
        assertNull(RewardDetector.tryDetect("Name: I rewarded 2 aspects to Steve", Component.literal("")));
        assertNull(RewardDetector.tryDetect("Name: X rewarded 99999999999999999999 Emeralds to Y",
                Component.literal("")));
    }

    @Test
    void detectsEmeraldRewardWithHoverText() {
        MutableComponent message = Component.empty()
                .append(TextFixtures.hoverText("Steve", "'s real name is NotSteve"))
                .append(" rewarded 1,000 Emeralds to Alex");

        RewardDetector.RewardResult result =
                RewardDetector.tryDetect("Steve rewarded 1,000 Emeralds to Alex", message);

        assertNotNull(result);
        assertEquals("NotSteve", result.sender());
        assertEquals("Alex", result.recipient());
        assertEquals(1000L, result.emeraldAmount());
    }
}
