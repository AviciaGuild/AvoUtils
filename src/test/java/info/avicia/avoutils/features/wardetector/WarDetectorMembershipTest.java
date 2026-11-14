package info.avicia.avoutils.features.wardetector;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarDetectorMembershipTest {

    private static WarDetector newTestDetector(AtomicReference<List<String>> nearbyPlayers) {
        return new WarDetector(() -> false, nearbyPlayers::get, () -> "LocalUser");
    }

    private static WarDetector.TowerState towerState(long timestamp, long health, double defense) {
        return new WarDetector.TowerState(health, defense, 1_200, 1_800, 2.0, timestamp);
    }

    @Test
    void resetClearsAllActiveWarState() {
        AtomicReference<List<String>> nearby = new AtomicReference<>(List.of("Steve", "Alex"));
        WarDetector detector = newTestDetector(nearby);
        WarDetector.TowerState initial = towerState(1_000L, 450_000L, 25.0);
        WarDetector.TowerState current = towerState(11_000L, 0L, 25.0);

        assertFalse(detector.isTrackingWar());
        detector.updateState(1_000L, new WarDetector.BattleSnapshot("Detlas", initial, current));
        assertTrue(detector.isTrackingWar());

        assertNotNull(detector.tryDetectOutcome("Territory Captured! Captured \"Detlas\""));
        assertEquals("Detlas", detector.getActiveTerritory());
        assertEquals(2, detector.getActiveWarrers().size());
        assertTrue(detector.isSubmissionSent());
        assertFalse(detector.isTrackingWar());

        detector.reset();

        assertNull(detector.getActiveTerritory());
        assertNull(detector.getActiveInitialState());
        assertNull(detector.getActiveCurrentState());
        assertTrue(detector.getActiveWarrers().isEmpty());
        assertFalse(detector.isSubmissionSent());
        assertFalse(detector.isTrackingWar());
    }

    @Test
    void sanitizeWarrersFiltersInvalidAndDeduplicatesCaseInsensitively() {
        List<String> result = WarDetector.sanitizeWarrers(
                List.of(" Steve ", "Alex", "bad name with spaces", "ab", "TooLongUsername12345678",
                        "Steve", "steve", "Alex", "Valid_123")
        );

        assertEquals(List.of("Steve", "Alex", "Valid_123"), result);
    }

    @Test
    void sanitizeWarrersHandlesEmptyAndNullCollections() {
        assertTrue(WarDetector.sanitizeWarrers(null).isEmpty());
        assertTrue(WarDetector.sanitizeWarrers(List.of()).isEmpty());
    }

    @Test
    void activeWarrersAccumulatesAcrossTicksCatchingBriefUnvanishes() {
        AtomicReference<List<String>> nearby = new AtomicReference<>(List.of("PlayerOne"));
        WarDetector detector = newTestDetector(nearby);
        WarDetector.TowerState state = towerState(1_000L, 100_000L, 0.0);
        WarDetector.BattleSnapshot snapshot = new WarDetector.BattleSnapshot("Detlas", state, state);

        detector.updateState(1_000L, snapshot);
        assertEquals(Set.of("PlayerOne"), detector.getActiveWarrers());

        // Single-tick unvanish on the very next 50ms tick
        nearby.set(List.of("PlayerOne", "VanishedAssassin"));
        detector.updateState(1_050L, snapshot);
        assertEquals(Set.of("PlayerOne", "VanishedAssassin"), detector.getActiveWarrers());

        // Re-vanishes; retained in activeWarrers
        nearby.set(List.of("PlayerOne"));
        detector.updateState(1_100L, snapshot);
        assertEquals(Set.of("PlayerOne", "VanishedAssassin"), detector.getActiveWarrers());
    }

    @Test
    void midWarBossBarFlickerAndKitingPreserveWarrersInitialStatsDurationAndDps() {
        AtomicReference<List<String>> nearby = new AtomicReference<>(List.of("LocalUser", "Early_Death"));
        WarDetector detector = newTestDetector(nearby);
        // 300,000 HP at 25% def -> 400,000 EHP
        WarDetector.TowerState originalInitial = towerState(1_000L, 300_000L, 25.0);
        WarDetector.BattleSnapshot firstSnapshot =
                new WarDetector.BattleSnapshot("Detlas", originalInitial, originalInitial);

        detector.updateState(1_000L, firstSnapshot);
        assertEquals("Detlas", detector.getActiveTerritory());
        assertEquals(Set.of("LocalUser", "Early_Death"), detector.getActiveWarrers());

        // Early_Death dies; local player kites outside boss-bar range for 10s (< GRACE_PERIOD_MS)
        detector.updateState(5_000L, null);
        detector.updateState(15_000L, null);
        assertEquals("Detlas", detector.getActiveTerritory());

        // Stepping back into boss-bar range causes Wynntils to mint a new WarBattleInfo with a new timestamp
        nearby.set(List.of("LocalUser", "LateTeammate"));
        WarDetector.TowerState midWarInitial = towerState(15_500L, 150_000L, 25.0);
        WarDetector.TowerState destroyedCurrent = towerState(21_000L, 0L, 25.0);
        WarDetector.BattleSnapshot readdedBarSnapshot =
                new WarDetector.BattleSnapshot("Detlas", midWarInitial, destroyedCurrent);
        detector.updateState(21_000L, readdedBarSnapshot);

        assertEquals("Detlas", detector.getActiveTerritory());
        assertSame(originalInitial, detector.getActiveInitialState());
        assertSame(destroyedCurrent, detector.getActiveCurrentState());

        // Capture outcome uses original initial HP (300k), full 20s duration (1s -> 21s), and 400k / 20s = 20k DPS
        WarDetector.WarResult result =
                detector.tryDetectOutcome("Territory Captured! Captured \"Detlas\" and +410 Seasonal Rating");
        assertNotNull(result);
        assertEquals("Captured", result.outcome());
        assertEquals("Detlas", result.territory());
        assertEquals(new WarDetector.TowerStats(300_000L, 25.0, 1_200, 1_800, 2.0), result.tower());
        assertEquals(20L, result.durationSeconds());
        assertEquals(20_000L, result.dps());
        assertEquals(List.of("LocalUser", "Early_Death", "LateTeammate"), result.warrers());
    }

    @Test
    void warExitGracePeriodDoesNotSampleMainWorldBystandersAndResetsAfterExpiry() {
        AtomicBoolean sampledOnMainWorld = new AtomicBoolean(false);
        AtomicReference<List<String>> nearby = new AtomicReference<>(List.of("LocalUser", "WarTeammate"));
        WarDetector detector = new WarDetector(
                () -> false,
                () -> {
                    sampledOnMainWorld.set(true);
                    return nearby.get();
                },
                () -> "LocalUser");

        WarDetector.TowerState state = towerState(1_000L, 200_000L, 0.0);
        detector.updateState(1_000L, new WarDetector.BattleSnapshot("Nemract", state, state));

        sampledOnMainWorld.set(false);
        nearby.set(List.of("LocalUser", "DetlasBystander"));
        detector.updateState(2_000L, null);

        assertFalse(sampledOnMainWorld.get());
        assertEquals("Nemract", detector.getActiveTerritory());
        assertEquals(Set.of("LocalUser", "WarTeammate"), detector.getActiveWarrers());

        // Still within grace period
        detector.updateState(2_000L + WarDetector.GRACE_PERIOD_MS, null);
        assertEquals("Nemract", detector.getActiveTerritory());

        // Past grace period without outcome chat -> resets
        detector.updateState(2_000L + WarDetector.GRACE_PERIOD_MS + 1L, null);
        assertNull(detector.getActiveTerritory());
        assertTrue(detector.getActiveWarrers().isEmpty());
    }

    @Test
    void outcomeTimeOnlyMergesNearbyPlayersWhileTowerBarIsStillActive() {
        AtomicBoolean towerBarActive = new AtomicBoolean(false);
        AtomicReference<List<String>> nearby = new AtomicReference<>(List.of("LocalUser", "WarMate"));
        WarDetector detector = new WarDetector(towerBarActive::get, nearby::get, () -> "LocalUser");

        WarDetector.TowerState start = towerState(1_000L, 100_000L, 0.0);
        WarDetector.TowerState end = towerState(6_000L, 0L, 0.0);
        detector.updateState(1_000L, new WarDetector.BattleSnapshot("Detlas", start, end));

        // Teleported back to Detlas right before outcome chat arrives -> tower bar is gone
        nearby.set(List.of("LocalUser", "DetlasBystander"));
        towerBarActive.set(false);
        WarDetector.WarResult result = detector.tryDetectOutcome("Territory Captured!");
        assertNotNull(result);
        assertEquals(List.of("LocalUser", "WarMate"), result.warrers());
    }

    @Test
    void postSubmissionTowerBarDisappearanceResetsCleanlyForBackToBackSameTerritoryWar() {
        AtomicReference<List<String>> nearby = new AtomicReference<>(List.of("LocalUser", "WarOneMate"));
        WarDetector detector = newTestDetector(nearby);

        WarDetector.TowerState start1 = towerState(1_000L, 100_000L, 0.0);
        WarDetector.TowerState end1 = towerState(6_000L, 100_000L, 0.0);
        detector.updateState(1_000L, new WarDetector.BattleSnapshot("Mangled Lake", start1, end1));

        WarDetector.WarResult loss = detector.tryDetectOutcome("You lost the war for Mangled Lake!");
        assertNotNull(loss);
        assertEquals("Failed", loss.outcome());
        assertEquals(List.of("LocalUser", "WarOneMate"), loss.warrers());

        // Teleported back to main world -> immediate clean reset
        nearby.set(List.of("LocalUser", "DetlasBystander"));
        detector.updateState(7_000L, null);
        assertNull(detector.getActiveTerritory());
        assertTrue(detector.getActiveWarrers().isEmpty());
        assertFalse(detector.isSubmissionSent());

        // Re-entering Mangled Lake minutes later starts completely fresh
        nearby.set(List.of("LocalUser", "WarTwoMate"));
        WarDetector.TowerState start2 = towerState(60_000L, 200_000L, 0.0);
        WarDetector.TowerState end2 = towerState(70_000L, 0L, 0.0);
        detector.updateState(60_000L, new WarDetector.BattleSnapshot("Mangled Lake", start2, end2));

        WarDetector.WarResult win = detector.tryDetectOutcome("Territory Captured!");
        assertNotNull(win);
        assertEquals("Captured", win.outcome());
        assertEquals(List.of("LocalUser", "WarTwoMate"), win.warrers());
    }

    @Test
    void tryDetectOutcomeReturnsNullWhenNoActiveBattle() {
        WarDetector detector = newTestDetector(new AtomicReference<>(List.of()));
        assertNull(detector.tryDetectOutcome("Territory Captured! Captured \"Detlas Suburbs\""));
        assertNull(detector.tryDetectOutcome("You lost the war for Detlas Suburbs!"));
    }

    @Test
    void tryDetectOutcomeReturnsNullWhenSubmissionAlreadySent() {
        WarDetector detector = newTestDetector(new AtomicReference<>(List.of("LocalUser")));
        WarDetector.TowerState state = towerState(1_000L, 100_000L, 0.0);
        detector.updateState(1_000L, new WarDetector.BattleSnapshot("Detlas", state, state));

        assertNotNull(detector.tryDetectOutcome("Territory Captured!"));
        assertNull(detector.tryDetectOutcome("Territory Captured!"));
    }

    @Test
    void tryDetectOutcomeIgnoresUnrelatedAndPlayerSpoofedChat() {
        WarDetector detector = newTestDetector(new AtomicReference<>(List.of("LocalUser")));
        WarDetector.TowerState state = towerState(1_000L, 100_000L, 0.0);
        detector.updateState(1_000L, new WarDetector.BattleSnapshot("Detlas", state, state));

        assertNull(detector.tryDetectOutcome("Good luck with the war everyone!"));
        assertNull(detector.tryDetectOutcome("Random player joined the game"));
        assertNull(detector.tryDetectOutcome("[AVO] Chief Jokester: Territory Captured!"));
        assertNull(detector.tryDetectOutcome("Jokester: You lost the war for Detlas!"));
        assertNull(detector.tryDetectOutcome("[Jokester -> You] Territory Captured!"));
        assertFalse(detector.isSubmissionSent());

        // Multiline system banner with colons after the outcome phrase is still accepted
        WarDetector.WarResult bannerResult =
                detector.tryDetectOutcome("Territory Captured!\nCaptured \"Detlas\"\nTime: 01:23");
        assertNotNull(bannerResult);
        assertEquals("Captured", bannerResult.outcome());
    }

    @Test
    void intraTickFinalBlowUpdatesCurrentStateBeforeOutcomeFormatting() {
        WarDetector detector = newTestDetector(new AtomicReference<>(List.of("LocalUser")));
        WarDetector.TowerState initial = towerState(1_000L, 100_000L, 0.0);
        AtomicReference<WarDetector.TowerState> liveCurrent =
                new AtomicReference<>(towerState(9_000L, 20_000L, 0.0));

        detector.updateState(
                9_000L,
                new WarDetector.BattleSnapshot("Detlas", initial, liveCurrent::get));

        // Final killing blow arrives in a boss-bar packet on the same tick right before outcome chat
        liveCurrent.set(towerState(11_000L, 0L, 0.0));

        WarDetector.WarResult result = detector.tryDetectOutcome("Territory Captured!");
        assertNotNull(result);
        assertEquals(10L, result.durationSeconds());
        assertEquals(10_000L, result.dps());
    }
}
