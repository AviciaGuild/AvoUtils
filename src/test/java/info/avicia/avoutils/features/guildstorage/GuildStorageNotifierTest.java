package info.avicia.avoutils.features.guildstorage;

import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import info.avicia.avoutils.testutil.TestReflection;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GuildStorageNotifierTest {

    @Test
    void parseSnapshotLinesParsesEmeraldsAndAspects() {
        GuildStorageNotifier.StorageSnapshot snapshot = GuildStorageNotifier.parseSnapshotLines(
                List.of("Emeralds: 1,000 / 5,000", "Aspects: 500 / 1,000"));

        assertNotNull(snapshot);
        assertEquals(1000L, snapshot.emeraldCurrent());
        assertEquals(5000L, snapshot.emeraldMax());
        assertEquals(500L, snapshot.aspectCurrent());
        assertEquals(1000L, snapshot.aspectMax());
    }

    @Test
    void parseSnapshotLinesReturnsNullForUnavailableRewards() {
        assertNull(GuildStorageNotifier.parseSnapshotLines(
                List.of("Emeralds: 1,000 / 5,000", "Rewards are unavailable for this rank")));
    }

    @Test
    void parseSnapshotLinesReturnsNullForIncompleteData() {
        assertNull(GuildStorageNotifier.parseSnapshotLines(List.of("Emeralds: 1,000 / 5,000")));
        assertNull(GuildStorageNotifier.parseSnapshotLines(null));
        assertNull(GuildStorageNotifier.parseSnapshotLines(List.of()));
    }

    @Test
    void parseSnapshotLinesHandlesMalformedNumbers() {
        assertNull(GuildStorageNotifier.parseSnapshotLines(
                List.of("Emeralds: , / ,", "Aspects: 100 / 200")));
    }

    @Test
    void onRaidDeltaIncreasesCounts() {
        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class);
             MockedStatic<AvoWebSocketManager> ws = mockStatic(AvoWebSocketManager.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            AvoWebSocketManager manager = mock(AvoWebSocketManager.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            ws.when(AvoWebSocketManager::getInstance).thenReturn(manager);
            when(authService.isGuildMember()).thenReturn(true);

            GuildStorageNotifier notifier = seededNotifier();
            notifier.onRaidDelta(10, 5);

            assertEquals(20L, (Long) TestReflection.get(notifier, "emeraldCurrent"));
            assertEquals(25L, (Long) TestReflection.get(notifier, "aspectCurrent"));
        }
    }

    @Test
    void onRewardDeltaClampsAtZero() {
        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class);
             MockedStatic<AvoWebSocketManager> ws = mockStatic(AvoWebSocketManager.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            AvoWebSocketManager manager = mock(AvoWebSocketManager.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            ws.when(AvoWebSocketManager::getInstance).thenReturn(manager);
            when(authService.isGuildMember()).thenReturn(true);

            GuildStorageNotifier notifier = seededNotifier();
            notifier.onRewardDelta(-200, -200);

            assertEquals(0L, (Long) TestReflection.get(notifier, "emeraldCurrent"));
            assertEquals(0L, (Long) TestReflection.get(notifier, "aspectCurrent"));
        }
    }

    @Test
    void resetClearsAllTrackedState() {
        GuildStorageNotifier notifier = seededNotifier();
        notifier.reset();

        assertEquals(-1L, (Long) TestReflection.get(notifier, "emeraldCurrent"));
        assertEquals(-1L, (Long) TestReflection.get(notifier, "emeraldMax"));
        assertEquals(-1L, (Long) TestReflection.get(notifier, "aspectCurrent"));
        assertEquals(-1L, (Long) TestReflection.get(notifier, "aspectMax"));
        assertEquals(Boolean.FALSE, TestReflection.get(notifier, "emeraldNotified"));
        assertEquals(Boolean.FALSE, TestReflection.get(notifier, "aspectNotified"));
    }

    @Test
    void applyRemoteSnapshotUpdatesStateWhenNotLocallyAuthoritative() {
        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class);
             MockedStatic<AvoWebSocketManager> ws = mockStatic(AvoWebSocketManager.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            AvoWebSocketManager manager = mock(AvoWebSocketManager.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            ws.when(AvoWebSocketManager::getInstance).thenReturn(manager);
            when(authService.isGuildMember()).thenReturn(true);

            GuildStorageNotifier notifier = seededNotifier();
            invokeApplyRemoteSnapshot(notifier, 55L, 100L, 44L, 100L);

            assertEquals(55L, (Long) TestReflection.get(notifier, "emeraldCurrent"));
            assertEquals(44L, (Long) TestReflection.get(notifier, "aspectCurrent"));
        }
    }

    private static void invokeApplyRemoteSnapshot(GuildStorageNotifier notifier,
                                                  long emCur, long emMax, long asCur, long asMax) {
        try {
            Method method = GuildStorageNotifier.class.getDeclaredMethod(
                    "applyRemoteSnapshot", long.class, long.class, long.class, long.class);
            method.setAccessible(true);
            method.invoke(notifier, emCur, emMax, asCur, asMax);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke applyRemoteSnapshot", e);
        }
    }

    private static GuildStorageNotifier seededNotifier() {
        GuildStorageNotifier notifier = new GuildStorageNotifier();
        TestReflection.set(notifier, "config", new ModConfig());
        TestReflection.set(notifier, "emeraldCurrent", 10L);
        TestReflection.set(notifier, "emeraldMax", 100L);
        TestReflection.set(notifier, "aspectCurrent", 20L);
        TestReflection.set(notifier, "aspectMax", 100L);
        return notifier;
    }
}
