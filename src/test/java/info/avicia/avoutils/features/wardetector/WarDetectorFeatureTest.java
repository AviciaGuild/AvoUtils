package info.avicia.avoutils.features.wardetector;

import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.core.util.WynncraftServerPolicy.Scope;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WarDetectorFeatureTest {

    @BeforeEach
    void setupPolicy() {
        WynncraftServerPolicy.setScopeOverride(() -> Scope.MAIN);
    }

    @AfterEach
    void resetPolicy() {
        WynncraftServerPolicy.setScopeOverride(null);
    }

    private void withMockedServices(boolean connected, boolean guildMember, Consumer<AvoWebSocketManager> testBody) {
        try (MockedStatic<AvoWebSocketManager> ws = mockStatic(AvoWebSocketManager.class);
             MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class);
             MockedStatic<AvoUtilsMod> mod = mockStatic(AvoUtilsMod.class)) {

            AvoWebSocketManager manager = mock(AvoWebSocketManager.class);
            AvoAuthService authService = mock(AvoAuthService.class);
            AvoUtilsMod modInstance = mock(AvoUtilsMod.class);

            ws.when(AvoWebSocketManager::getInstance).thenReturn(manager);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            mod.when(AvoUtilsMod::getInstance).thenReturn(modInstance);

            when(manager.isConnected()).thenReturn(connected);
            when(authService.isGuildMember()).thenReturn(guildMember);

            testBody.accept(manager);
        }
    }

    @Test
    void isGuildMemberDelegatesToAuthService() {
        withMockedServices(true, true, manager -> {
            WarDetectorFeature feature = new WarDetectorFeature();
            assertTrue(feature.isGuildMember());
        });

        withMockedServices(true, false, manager -> {
            WarDetectorFeature feature = new WarDetectorFeature();
            assertFalse(feature.isGuildMember());
        });
    }

    @Test
    void initializeRegistersConnectionDemand() {
        withMockedServices(true, true, manager -> {
            WarDetectorFeature feature = new WarDetectorFeature();
            feature.initialize(new ModConfig());

            verify(manager).registerConnectionDemand(eq("wardetector"), any());
        });
    }

    @Test
    void onSystemChatIgnoresNullOrDisconnectedOrNonMember() {
        withMockedServices(false, true, manager -> {
            WarDetectorFeature feature = new WarDetectorFeature();
            feature.onSystemChat(Component.literal("Territory Captured"));
            verify(manager, never()).sendEvent(anyString(), any(JsonObject.class));
        });

        withMockedServices(true, false, manager -> {
            WarDetectorFeature feature = new WarDetectorFeature();
            feature.onSystemChat(Component.literal("Territory Captured"));
            verify(manager, never()).sendEvent(anyString(), any(JsonObject.class));
        });

        withMockedServices(true, true, manager -> {
            WarDetectorFeature feature = new WarDetectorFeature();
            feature.onSystemChat(null);
            verify(manager, never()).sendEvent(anyString(), any(JsonObject.class));
        });
    }

    @Test
    void onSystemChatDoesNotSendWhenNoActiveWar() {
        withMockedServices(true, true, manager -> {
            WarDetectorFeature feature = new WarDetectorFeature();
            feature.onSystemChat(Component.literal("Territory Captured"));
            verify(manager, never()).sendEvent(anyString(), any(JsonObject.class));
        });
    }

    @Test
    void onSystemChatDispatchesStructuredWarPayloadWhenActiveWarCompletes() {
        withMockedServices(true, true, manager -> {
            WarDetector detector = new WarDetector(() -> false, () -> List.of("LocalUser", "The_PixelChicken"), () -> "LocalUser");
            WarDetector.TowerState start = new WarDetector.TowerState(200_000L, 50.0, 1_000, 1_500, 2.0, 1_000L);
            WarDetector.TowerState end = new WarDetector.TowerState(0L, 50.0, 1_000, 1_500, 2.0, 11_000L);
            detector.updateState(1_000L, new WarDetector.BattleSnapshot("Detlas", start, end));

            WarDetectorFeature feature = new WarDetectorFeature(detector);
            feature.onSystemChat(Component.literal("Territory Captured! Captured \"Detlas\""));

            ArgumentCaptor<JsonObject> payloadCaptor = ArgumentCaptor.forClass(JsonObject.class);
            verify(manager).sendEvent(eq("guild_war_result"), payloadCaptor.capture());

            JsonObject payload = payloadCaptor.getValue();
            assertEquals("Detlas", payload.get("territory").getAsString());
            assertEquals("Captured", payload.get("outcome").getAsString());
            JsonObject tower = payload.getAsJsonObject("tower");
            assertEquals(200_000L, tower.get("hp").getAsLong());
            assertEquals(50.0, tower.get("defense").getAsDouble());
            assertEquals(1_000, tower.get("damage_low").getAsInt());
            assertEquals(1_500, tower.get("damage_high").getAsInt());
            assertEquals(2.0, tower.get("attack_speed").getAsDouble());
            assertEquals(2, payload.getAsJsonArray("warrers").size());
            assertEquals("LocalUser", payload.getAsJsonArray("warrers").get(0).getAsString());
            assertEquals("The_PixelChicken", payload.getAsJsonArray("warrers").get(1).getAsString());
            assertFalse(payload.has("message"));
            assertFalse(payload.has("stats"));
            assertEquals(10L, payload.get("duration_seconds").getAsLong());
            assertEquals(40_000L, payload.get("dps").getAsLong());
        });
    }

    @Test
    void onServerScopeChangedResetsWarDetectorWhenLeavingMain() {
        WarDetector detector = new WarDetector(() -> false, () -> List.of("LocalUser"), () -> "LocalUser");
        WarDetector.TowerState state = new WarDetector.TowerState(100_000L, 0.0, 500, 800, 1.5, 1_000L);
        detector.updateState(1_000L, new WarDetector.BattleSnapshot("Detlas", state, state));
        WarDetectorFeature feature = new WarDetectorFeature(detector);

        feature.onServerScopeChanged(Scope.MAIN);
        assertEquals("Detlas", detector.getActiveTerritory());

        feature.onServerScopeChanged(Scope.BETA);
        assertNull(detector.getActiveTerritory());
    }
}
