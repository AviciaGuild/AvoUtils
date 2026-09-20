package info.avicia.avoutils.features.chatbridge;

import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import info.avicia.avoutils.features.guildstorage.GuildStorageNotifier;
import info.avicia.avoutils.testutil.TestReflection;
import info.avicia.avoutils.testutil.TextFixtures;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import net.minecraft.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatBridgeFeatureTest {

    private static final int GUILD_CHAT_COLOR = 0x55FFFF;

    @BeforeEach
    void setUp() {
        WynncraftServerPolicy.setScopeOverride(() -> WynncraftServerPolicy.Scope.MAIN);
    }

    @AfterEach
    void tearDown() {
        WynncraftServerPolicy.setScopeOverride(null);
    }

    @Test
    void leadingGuildColorIsDetectedOnRootStyle() throws Exception {
        assertTrue(hasLeadingGuildChatColor(TextFixtures.coloredText("hi", GUILD_CHAT_COLOR)));
    }

    @Test
    void leadingGuildColorIsDetectedOnLeafStyle() throws Exception {
        Text message = Text.empty().append(TextFixtures.coloredText("hi", GUILD_CHAT_COLOR));
        assertTrue(hasLeadingGuildChatColor(message));
    }

    @Test
    void otherColorsAreNotGuildChat() throws Exception {
        assertFalse(hasLeadingGuildChatColor(TextFixtures.coloredText("hi", 0xFF0000)));
        assertFalse(hasLeadingGuildChatColor(Text.literal("hi")));
    }

    private static boolean hasLeadingGuildChatColor(Text message) throws Exception {
        return TestReflection.invokeStatic(ChatBridgeFeature.class, "hasLeadingGuildChatColor",
                new Class[]{Text.class}, message);
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
            when(modInstance.getFeature(GuildStorageNotifier.class)).thenReturn(null);

            testBody.accept(manager);
        }
    }

    @Test
    void relaysGuildChatWhenBridgeActive() {
        withMockedServices(true, true, manager -> {
            ChatBridgeFeature feature = new ChatBridgeFeature();
            feature.initialize(new ModConfig());
            feature.onSystemChat(TextFixtures.coloredText("Steve: hello guild", GUILD_CHAT_COLOR));
            verify(manager).sendEvent(eq("guild_chat"), any(JsonObject.class));
        });
    }

    @Test
    void doesNotRelayWhenBridgeDisabled() {
        withMockedServices(true, true, manager -> {
            ModConfig config = new ModConfig();
            config.chatBridgeEnabled = false;
            ChatBridgeFeature feature = new ChatBridgeFeature();
            feature.initialize(config);
            feature.onSystemChat(TextFixtures.coloredText("Steve: hello", GUILD_CHAT_COLOR));
            verify(manager, never()).sendEvent(anyString(), any(JsonObject.class));
        });
    }

    @Test
    void ignoresMessagesWithoutGuildChatColor() {
        withMockedServices(true, true, manager -> {
            ChatBridgeFeature feature = new ChatBridgeFeature();
            feature.initialize(new ModConfig());
            feature.onSystemChat(Text.literal("Steve: hello"));
            verify(manager, never()).sendEvent(anyString(), any(JsonObject.class));
        });
    }

    @Test
    void doesNotRelayWhenNotConnected() {
        withMockedServices(false, true, manager -> {
            ChatBridgeFeature feature = new ChatBridgeFeature();
            feature.initialize(new ModConfig());
            feature.onSystemChat(TextFixtures.coloredText("Steve: hello", GUILD_CHAT_COLOR));
            verify(manager, never()).sendEvent(anyString(), any(JsonObject.class));
        });
    }

    @Test
    void toggleBridgeAllowsTogglingWhenAuthorized() {
        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class);
             MockedStatic<AvoWebSocketManager> ws = mockStatic(AvoWebSocketManager.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            AvoWebSocketManager manager = mock(AvoWebSocketManager.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            ws.when(AvoWebSocketManager::getInstance).thenReturn(manager);

            doCallRealMethod().when(authService).runIfGuildMember(any(), any());
            when(authService.isGuildMember()).thenReturn(true);

            ModConfig config = new ModConfig();
            config.chatBridgeEnabled = false;
            ChatBridgeFeature feature = new ChatBridgeFeature();
            feature.initialize(config);

            feature.toggleBridge();
            assertTrue(config.chatBridgeEnabled);

            feature.toggleBridge();
            assertFalse(config.chatBridgeEnabled);
        }
    }

    @Test
    void toggleBridgeRejectsWhenUnauthorized() {
        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class);
             MockedStatic<AvoWebSocketManager> ws = mockStatic(AvoWebSocketManager.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            AvoWebSocketManager manager = mock(AvoWebSocketManager.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            ws.when(AvoWebSocketManager::getInstance).thenReturn(manager);

            doCallRealMethod().when(authService).runIfGuildMember(any(), any());
            when(authService.isGuildMember()).thenReturn(false);
            when(authService.getCachedGuildMember()).thenReturn(false);

            ModConfig config = new ModConfig();
            config.chatBridgeEnabled = false;
            ChatBridgeFeature feature = new ChatBridgeFeature();
            feature.initialize(config);

            feature.toggleBridge();
            assertFalse(config.chatBridgeEnabled);
        }
    }

    @Test
    void toggleBridgeResolvesMembershipWhenUncached() {
        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class);
             MockedStatic<AvoWebSocketManager> ws = mockStatic(AvoWebSocketManager.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            AvoWebSocketManager manager = mock(AvoWebSocketManager.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            ws.when(AvoWebSocketManager::getInstance).thenReturn(manager);

            doCallRealMethod().when(authService).runIfGuildMember(any(), any());
            when(authService.isGuildMember()).thenReturn(false);
            when(authService.getCachedGuildMember()).thenReturn(null);
            when(authService.resolveGuildMembership()).thenReturn(CompletableFuture.completedFuture(true));

            ModConfig config = new ModConfig();
            config.chatBridgeEnabled = false;
            ChatBridgeFeature feature = new ChatBridgeFeature();
            feature.initialize(config);

            feature.toggleBridge();
            verify(authService).resolveGuildMembership();
        }
    }
}
