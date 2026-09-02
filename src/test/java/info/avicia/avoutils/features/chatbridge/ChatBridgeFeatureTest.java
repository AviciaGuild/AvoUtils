package info.avicia.avoutils.features.chatbridge;

import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import info.avicia.avoutils.features.guildstorage.GuildStorageNotifier;
import info.avicia.avoutils.testutil.TextFixtures;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatBridgeFeatureTest {

    private static final int GUILD_CHAT_COLOR = 0x55FFFF;

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
        Method method = ChatBridgeFeature.class.getDeclaredMethod("hasLeadingGuildChatColor", Text.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, message);
    }

    @Test
    void relaysGuildChatWhenBridgeActive() {
        try (MockedStatic<AvoWebSocketManager> ws = mockStatic(AvoWebSocketManager.class);
             MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class);
             MockedStatic<AvoUtilsMod> mod = mockStatic(AvoUtilsMod.class)) {

            AvoWebSocketManager manager = mock(AvoWebSocketManager.class);
            AvoAuthService authService = mock(AvoAuthService.class);
            AvoUtilsMod modInstance = mock(AvoUtilsMod.class);

            ws.when(AvoWebSocketManager::getInstance).thenReturn(manager);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            mod.when(AvoUtilsMod::getInstance).thenReturn(modInstance);

            when(manager.isConnected()).thenReturn(true);
            when(authService.isGuildMember()).thenReturn(true);
            when(modInstance.getFeature(GuildStorageNotifier.class)).thenReturn(null);

            ChatBridgeFeature feature = new ChatBridgeFeature();
            feature.initialize(new ModConfig());

            feature.onSystemChat(TextFixtures.coloredText("Steve: hello guild", GUILD_CHAT_COLOR));

            verify(manager).sendEvent(eq("guild_chat"), any(JsonObject.class));
        }
    }

    @Test
    void doesNotRelayWhenBridgeDisabled() {
        try (MockedStatic<AvoWebSocketManager> ws = mockStatic(AvoWebSocketManager.class);
             MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class);
             MockedStatic<AvoUtilsMod> mod = mockStatic(AvoUtilsMod.class)) {

            AvoWebSocketManager manager = mock(AvoWebSocketManager.class);
            AvoAuthService authService = mock(AvoAuthService.class);
            AvoUtilsMod modInstance = mock(AvoUtilsMod.class);

            ws.when(AvoWebSocketManager::getInstance).thenReturn(manager);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            mod.when(AvoUtilsMod::getInstance).thenReturn(modInstance);

            when(manager.isConnected()).thenReturn(true);
            when(authService.isGuildMember()).thenReturn(true);
            when(modInstance.getFeature(GuildStorageNotifier.class)).thenReturn(null);

            ModConfig config = new ModConfig();
            config.chatBridgeEnabled = false;
            ChatBridgeFeature feature = new ChatBridgeFeature();
            feature.initialize(config);

            feature.onSystemChat(TextFixtures.coloredText("Steve: hello", GUILD_CHAT_COLOR));

            verify(manager, never()).sendEvent(anyString(), any(JsonObject.class));
        }
    }

    @Test
    void ignoresMessagesWithoutGuildChatColor() {
        try (MockedStatic<AvoWebSocketManager> ws = mockStatic(AvoWebSocketManager.class);
             MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class);
             MockedStatic<AvoUtilsMod> mod = mockStatic(AvoUtilsMod.class)) {

            AvoWebSocketManager manager = mock(AvoWebSocketManager.class);
            AvoAuthService authService = mock(AvoAuthService.class);
            AvoUtilsMod modInstance = mock(AvoUtilsMod.class);

            ws.when(AvoWebSocketManager::getInstance).thenReturn(manager);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            mod.when(AvoUtilsMod::getInstance).thenReturn(modInstance);

            when(manager.isConnected()).thenReturn(true);
            when(authService.isGuildMember()).thenReturn(true);
            when(modInstance.getFeature(GuildStorageNotifier.class)).thenReturn(null);

            ChatBridgeFeature feature = new ChatBridgeFeature();
            feature.initialize(new ModConfig());

            feature.onSystemChat(Text.literal("Steve: hello"));

            verify(manager, never()).sendEvent(anyString(), any(JsonObject.class));
        }
    }
}
