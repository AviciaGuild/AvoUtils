package info.avicia.avoutils.features.chatbridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TerritoryEventDetectorTest {

    @Test
    void detectsEnemyTerritoryCapture() {
        TerritoryEventDetector detector = new TerritoryEventDetector();
        TerritoryEventDetector.Result result =
                detector.tryDetect("[SEQ] has taken control of Overtaken Outpost!");

        assertNotNull(result);
        assertEquals(TerritoryEventDetector.Action.CAPTURE, result.action());
        assertEquals("Overtaken Outpost", result.territory());
        assertEquals("SEQ", result.guildTag());
    }

    @Test
    void ignoresOwnGuildTerritoryCapture() {
        TerritoryEventDetector detector = new TerritoryEventDetector();
        assertNull(detector.tryDetect("[AVO] has taken control of Detlas!"));
        assertNull(detector.tryDetect("[avo] has taken control of Detlas!"));
    }

    @Test
    void detectsSuccessfulDefenseInNormalOrder() {
        long[] now = {1_000L};
        TerritoryEventDetector detector = new TerritoryEventDetector(() -> now[0]);

        assertNull(detector.tryDetect("Your guild has successfully defended Shineridge Orc Camp."));
        now[0] += 200L;
        TerritoryEventDetector.Result result = detector.tryDetect("[Nia] has lost the war!");

        assertNotNull(result);
        assertEquals(TerritoryEventDetector.Action.DEFENSE, result.action());
        assertEquals("Shineridge Orc Camp", result.territory());
        assertEquals("Nia", result.guildTag());
    }

    @Test
    void detectsSuccessfulDefenseInInvertedOrder() {
        long[] now = {1_000L};
        TerritoryEventDetector detector = new TerritoryEventDetector(() -> now[0]);

        assertNull(detector.tryDetect("[Nia] has lost the war!"));
        now[0] += 100L;
        TerritoryEventDetector.Result result =
                detector.tryDetect("Your guild has successfully defended Shineridge Orc Camp.");

        assertNotNull(result);
        assertEquals(TerritoryEventDetector.Action.DEFENSE, result.action());
        assertEquals("Shineridge Orc Camp", result.territory());
        assertEquals("Nia", result.guildTag());
    }

    @Test
    void detectsCombinedSinglePacketDefense() {
        TerritoryEventDetector detector = new TerritoryEventDetector();
        TerritoryEventDetector.Result result = detector.tryDetect(
                "Your guild has successfully defended Shineridge Orc Camp. [Nia] has lost the war!");

        assertNotNull(result);
        assertEquals(TerritoryEventDetector.Action.DEFENSE, result.action());
        assertEquals("Shineridge Orc Camp", result.territory());
        assertEquals("Nia", result.guildTag());
    }

    @Test
    void expiresUnpairedDefenseAfterWindow() {
        long[] now = {1_000L};
        TerritoryEventDetector detector = new TerritoryEventDetector(() -> now[0]);

        assertNull(detector.tryDetect("Your guild has successfully defended Shineridge Orc Camp."));
        now[0] += TerritoryEventDetector.DEFENSE_PAIR_WINDOW_MS + 1L;
        assertNull(detector.tryDetect("[Nia] has lost the war!"));
    }

    @Test
    void ignoresOwnGuildWarLossAndPlayerChatSpoofing() {
        TerritoryEventDetector detector = new TerritoryEventDetector();
        assertNull(detector.tryDetect("Your guild has successfully defended Detlas."));
        assertNull(detector.tryDetect("[AVO] has lost the war!"));

        detector.reset();
        assertNull(detector.tryDetect("Player: [SEQ] has taken control of Overtaken Outpost!"));
        assertNull(detector.tryDetect("Player -> You: [SEQ] has taken control of Overtaken Outpost!"));
    }
}
