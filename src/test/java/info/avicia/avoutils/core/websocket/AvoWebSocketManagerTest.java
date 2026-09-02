package info.avicia.avoutils.core.websocket;

import com.google.gson.JsonObject;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.testutil.TestReflection;
import net.minecraft.client.MinecraftClient;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvoWebSocketManagerTest {

    @Test
    void initializeThenDisconnectedByDefault() {
        AvoWebSocketManager.initialize(new ModConfig());
        AvoWebSocketManager manager = AvoWebSocketManager.getInstance();

        assertFalse(manager.isConnected());
        // No client attached: sending must not throw.
        manager.sendEvent("test", new JsonObject());
    }

    @Test
    void registerAndUnregisterConnectionDemand() {
        AvoWebSocketManager.initialize(new ModConfig());
        AvoWebSocketManager manager = AvoWebSocketManager.getInstance();

        manager.registerConnectionDemand("feature", () -> true);
        manager.unregisterConnectionDemand("feature");
    }

    @Test
    void sendEventForwardsToOpenClient() {
        AvoWebSocketManager.initialize(new ModConfig());
        AvoWebSocketManager manager = AvoWebSocketManager.getInstance();

        AvoWebSocketClient client = mock(AvoWebSocketClient.class);
        when(client.isOpen()).thenReturn(true);
        TestReflection.set(manager, "client", client);

        JsonObject payload = new JsonObject();
        manager.sendEvent("guild_chat", payload);

        verify(client).sendEvent("guild_chat", payload);
    }

    @Test
    void handleIncomingEventDispatchesToListeners() {
        try (MockedStatic<MinecraftClient> mc = mockStatic(MinecraftClient.class)) {
            MinecraftClient client = mock(MinecraftClient.class);
            mc.when(MinecraftClient::getInstance).thenReturn(client);
            doAnswer(inv -> {
                ((Runnable) inv.getArgument(0)).run();
                return null;
            }).when(client).execute(any(Runnable.class));

            AvoWebSocketManager.initialize(new ModConfig());
            AvoWebSocketManager manager = AvoWebSocketManager.getInstance();

            AtomicReference<JsonObject> captured = new AtomicReference<>();
            Consumer<JsonObject> listener = captured::set;
            manager.registerListener("guild_chat", listener);

            invokeHandleIncomingEvent(manager, "guild_chat", new JsonObject());

            assertNotNull(captured.get());
            manager.unregisterListener("guild_chat", listener);
        }
    }

    @Test
    void wsUriForConvertsHttpsToWss() throws Exception {
        assertEquals("wss://auth.avicia.info:8443/ws",
                AvoWebSocketManager.wsUriFor("https://auth.avicia.info:8443").toString());
    }

    @Test
    void wsUriForConvertsHttpToWsWithoutPort() throws Exception {
        assertEquals("ws://localhost/ws",
                AvoWebSocketManager.wsUriFor("http://localhost").toString());
    }

    @Test
    void baseBackoffDoublesAndCaps() {
        assertEquals(5_000L, AvoWebSocketManager.baseBackoffForFailures(0));
        assertEquals(10_000L, AvoWebSocketManager.baseBackoffForFailures(1));
        assertEquals(80_000L, AvoWebSocketManager.baseBackoffForFailures(4));
        assertEquals(120_000L, AvoWebSocketManager.baseBackoffForFailures(5));
        assertEquals(120_000L, AvoWebSocketManager.baseBackoffForFailures(100));
    }

    private static void invokeHandleIncomingEvent(AvoWebSocketManager manager, String type, JsonObject json) {
        try {
            Method method = AvoWebSocketManager.class
                    .getDeclaredMethod("handleIncomingEvent", String.class, JsonObject.class);
            method.setAccessible(true);
            method.invoke(manager, type, json);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke handleIncomingEvent", e);
        }
    }
}
