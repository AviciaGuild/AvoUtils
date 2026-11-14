package info.avicia.avoutils.features.chatbridge;

import com.google.gson.JsonObject;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import info.avicia.avoutils.testutil.TextFixtures;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    private void withMockedServices(boolean connected, boolean guildMember, Consumer<AvoWebSocketManager> testBody) {
        try (MockedStatic<AvoWebSocketManager> ws = mockStatic(AvoWebSocketManager.class);
             MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class)) {

            AvoWebSocketManager manager = mock(AvoWebSocketManager.class);
            AvoAuthService authService = mock(AvoAuthService.class);

            ws.when(AvoWebSocketManager::getInstance).thenReturn(manager);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);

            when(manager.isConnected()).thenReturn(connected);
            when(authService.isGuildMember()).thenReturn(guildMember);

            testBody.accept(manager);
        }
    }

    @Test
    void relaysGuildChatWithoutClientAvatarUrl() {
        withMockedServices(true, true, manager -> {
            ChatBridgeFeature feature = new ChatBridgeFeature();
            feature.initialize(new ModConfig());
            feature.onSystemChat(TextFixtures.coloredText("Steve: hello guild", GUILD_CHAT_COLOR));

            ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
            verify(manager).sendEvent(eq("guild_chat"), captor.capture());
            JsonObject payload = captor.getValue();
            assertEquals("Steve", payload.get("username").getAsString());
            assertEquals("hello guild", payload.get("message").getAsString());
            assertFalse(payload.has("avatar_url"));
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
            feature.onSystemChat(Component.literal("Steve: hello"));
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
    void relaysStructuredTerritoryCaptureAndDefenseEvents() {
        withMockedServices(true, true, manager -> {
            ChatBridgeFeature feature = new ChatBridgeFeature();
            feature.initialize(new ModConfig());

            feature.onSystemChat(TextFixtures.coloredText("[SEQ] has taken control of Overtaken Outpost!", 0xFF5555));
            ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
            verify(manager).sendEvent(eq("guild_territory_event"), captor.capture());
            JsonObject payload = captor.getValue();
            assertEquals("capture", payload.get("action").getAsString());
            assertEquals("Overtaken Outpost", payload.get("territory").getAsString());
            assertEquals("SEQ", payload.get("guild_tag").getAsString());
            assertFalse(payload.has("username"));
            assertFalse(payload.has("avatar_url"));
            assertFalse(payload.has("message"));
        });

        withMockedServices(true, true, manager -> {
            ChatBridgeFeature feature = new ChatBridgeFeature();
            feature.initialize(new ModConfig());

            feature.onSystemChat(TextFixtures.coloredText("Your guild has successfully defended Shineridge Orc Camp.", 0xFF5555));
            verify(manager, never()).sendEvent(anyString(), any(JsonObject.class));

            feature.onSystemChat(TextFixtures.coloredText("[Nia] has lost the war!", 0xFF5555));
            ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
            verify(manager).sendEvent(eq("guild_territory_event"), captor.capture());
            JsonObject payload = captor.getValue();
            assertEquals("defense", payload.get("action").getAsString());
            assertEquals("Shineridge Orc Camp", payload.get("territory").getAsString());
            assertEquals("Nia", payload.get("guild_tag").getAsString());
        });

        withMockedServices(true, true, manager -> {
            ChatBridgeFeature feature = new ChatBridgeFeature();
            feature.initialize(new ModConfig());

            feature.onSystemChat(Component.literal("[SEQ] has taken control of Overtaken Outpost!"));
            verify(manager, never()).sendEvent(anyString(), any(JsonObject.class));
        });
    }

    @Test
    void relaysStructuredAllianceMemberRaidRewardAndBankEventsWithoutFallthrough() {
        withMockedServices(true, true, manager -> {
            ChatBridgeFeature feature = new ChatBridgeFeature();
            feature.initialize(new ModConfig());

            feature.onSystemChat(TextFixtures.coloredText("Avicia formed an alliance with Silk Road", GUILD_CHAT_COLOR));
            ArgumentCaptor<JsonObject> allyCaptor = ArgumentCaptor.forClass(JsonObject.class);
            verify(manager, times(1)).sendEvent(eq("guild_ally_event"), allyCaptor.capture());
            assertEquals("our_form", allyCaptor.getValue().get("action").getAsString());
            assertEquals("Silk Road", allyCaptor.getValue().get("guild").getAsString());
            assertFalse(allyCaptor.getValue().has("username"));
            assertFalse(allyCaptor.getValue().has("avatar_url"));

            feature.onSystemChat(Component.literal("Avicia formed an alliance with Other Guild"));
            verify(manager, times(1)).sendEvent(eq("guild_ally_event"), any(JsonObject.class));

            feature.onSystemChat(TextFixtures.coloredText("New_Member has joined the guild, say hello!", GUILD_CHAT_COLOR));
            ArgumentCaptor<JsonObject> memberCaptor = ArgumentCaptor.forClass(JsonObject.class);
            verify(manager, times(1)).sendEvent(eq("guild_member_event"), memberCaptor.capture());
            assertEquals("join", memberCaptor.getValue().get("action").getAsString());
            assertEquals("New_Member", memberCaptor.getValue().get("target").getAsString());

            feature.onSystemChat(TextFixtures.coloredText(
                    "Steve finished NOL and claimed 2x Aspects and +100000m Guild Experience", GUILD_CHAT_COLOR));
            ArgumentCaptor<JsonObject> raidCaptor = ArgumentCaptor.forClass(JsonObject.class);
            verify(manager, times(1)).sendEvent(eq("guild_raid_completion"), raidCaptor.capture());
            assertEquals("Steve", raidCaptor.getValue().getAsJsonArray("players").get(0).getAsString());
            assertEquals("NOL", raidCaptor.getValue().get("raid").getAsString());
            assertEquals(2, raidCaptor.getValue().get("aspects").getAsInt());

            feature.onSystemChat(TextFixtures.coloredText("Steve_Bob rewarded a Guild Tome to Alex", GUILD_CHAT_COLOR));
            feature.onSystemChat(TextFixtures.coloredText("Steve_Bob rewarded a Guild Tome to Alex", GUILD_CHAT_COLOR));
            ArgumentCaptor<JsonObject> rewardCaptor = ArgumentCaptor.forClass(JsonObject.class);
            verify(manager, times(2)).sendEvent(eq("guild_reward"), rewardCaptor.capture());
            assertEquals("Steve_Bob", rewardCaptor.getValue().get("sender").getAsString());
            assertEquals(1L, rewardCaptor.getValue().get("tomes").getAsLong());

            feature.onSystemChat(TextFixtures.coloredText(
                    "Steve_Bob deposited 5 Emeralds to the Guild Bank (General)", GUILD_CHAT_COLOR));
            ArgumentCaptor<JsonObject> bankCaptor = ArgumentCaptor.forClass(JsonObject.class);
            verify(manager, times(1)).sendEvent(eq("guild_bank_event"), bankCaptor.capture());
            assertEquals("Steve_Bob", bankCaptor.getValue().get("player").getAsString());
            assertEquals("deposited", bankCaptor.getValue().get("action").getAsString());
            assertEquals("5 Emeralds", bankCaptor.getValue().get("item").getAsString());
            assertEquals("General", bankCaptor.getValue().get("tier").getAsString());
            assertFalse(bankCaptor.getValue().has("username"));
            assertFalse(bankCaptor.getValue().has("avatar_url"));
        });
    }
}
