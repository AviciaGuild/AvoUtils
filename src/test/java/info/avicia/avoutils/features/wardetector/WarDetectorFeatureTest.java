package info.avicia.avoutils.features.wardetector;

import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.core.util.WynncraftServerPolicy.Scope;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import net.minecraft.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.function.Consumer;

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
    @AfterEach
    void cleanState() {
        WarDetector.reset();
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
            feature.onSystemChat(Text.literal("Territory Captured"));
            verify(manager, never()).sendEvent(anyString(), any(JsonObject.class));
        });

        withMockedServices(true, false, manager -> {
            WarDetectorFeature feature = new WarDetectorFeature();
            feature.onSystemChat(Text.literal("Territory Captured"));
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
            feature.onSystemChat(Text.literal("Territory Captured"));
            verify(manager, never()).sendEvent(anyString(), any(JsonObject.class));
        });
    }

    @Test
    void onServerScopeChangedResetsWarDetectorWhenLeavingMain() throws Exception {
        setStaticField("activeBattleId", "Detlas:1000");
        WarDetectorFeature feature = new WarDetectorFeature();

        feature.onServerScopeChanged(Scope.MAIN);
        assertEquals("Detlas:1000", WarDetector.getActiveBattleId());

        feature.onServerScopeChanged(Scope.BETA);
        assertNull(WarDetector.getActiveBattleId());
    }

    private static void setStaticField(String fieldName, Object value) throws Exception {
        Field field = WarDetector.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void assertEquals(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
