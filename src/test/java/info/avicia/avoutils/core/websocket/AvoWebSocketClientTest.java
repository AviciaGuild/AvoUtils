package info.avicia.avoutils.core.websocket;

import com.google.gson.JsonObject;
import info.avicia.avoutils.core.auth.AvoAuthService;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.net.URI;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvoWebSocketClientTest {

    @Test
    void onMessageDispatchesTypedEnvelope() {
        @SuppressWarnings("unchecked")
        BiConsumer<String, JsonObject> handler = mock(BiConsumer.class);
        AvoWebSocketClient client = client(handler, () -> { }, code -> { });

        client.onMessage("{\"type\":\"guild_chat\",\"message\":\"hi\"}");

        verify(handler).accept(eq("guild_chat"), any(JsonObject.class));
    }

    @Test
    void onMessageIgnoresMalformedJson() {
        @SuppressWarnings("unchecked")
        BiConsumer<String, JsonObject> handler = mock(BiConsumer.class);
        AvoWebSocketClient client = client(handler, () -> { }, code -> { });

        client.onMessage("not json {");

        verify(handler, never()).accept(anyString(), any(JsonObject.class));
    }

    @Test
    void onMessageIgnoresEnvelopeWithoutType() {
        @SuppressWarnings("unchecked")
        BiConsumer<String, JsonObject> handler = mock(BiConsumer.class);
        AvoWebSocketClient client = client(handler, () -> { }, code -> { });

        client.onMessage("{\"message\":\"hi\"}");

        verify(handler, never()).accept(anyString(), any(JsonObject.class));
    }

    @Test
    void onCloseInvalidatesTokenOnAuthFailure() {
        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);

            IntConsumer onClose = mock(IntConsumer.class);
            AvoWebSocketClient client = client((t, j) -> { }, () -> { }, onClose);

            client.onClose(AvoWebSocketClient.AUTH_FAILURE_CLOSE_CODE, "unauthorized", true);

            verify(authService).invalidateToken();
            verify(onClose).accept(AvoWebSocketClient.AUTH_FAILURE_CLOSE_CODE);
        }
    }

    @Test
    void onOpenRunsCallback() {
        Runnable onOpen = mock(Runnable.class);
        AvoWebSocketClient client = client((t, j) -> { }, onOpen, code -> { });

        ServerHandshake handshake = mock(ServerHandshake.class);
        when(handshake.getHttpStatus()).thenReturn((short) 101);

        client.onOpen(handshake);

        verify(onOpen).run();
    }

    @Test
    void onMessageDropsOversizedMessages() {
        @SuppressWarnings("unchecked")
        BiConsumer<String, JsonObject> handler = mock(BiConsumer.class);
        AvoWebSocketClient client = client(handler, () -> { }, code -> { });

        client.onMessage("x".repeat(300_000));

        verify(handler, never()).accept(anyString(), any(JsonObject.class));
    }

    @Test
    void onCloseInvalidatesTokenOnProtocolError() {
        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);

            IntConsumer onClose = mock(IntConsumer.class);
            AvoWebSocketClient client = client((t, j) -> { }, () -> { }, onClose);

            client.onClose(1002, "protocol error", true);

            verify(authService).invalidateToken();
        }
    }

    @Test
    void onErrorLogsWithoutThrowing() {
        AvoWebSocketClient client = client((t, j) -> { }, () -> { }, code -> { });

        client.onError(new RuntimeException("boom"));
    }

    private static AvoWebSocketClient client(BiConsumer<String, JsonObject> handler,
                                             Runnable onOpen,
                                             IntConsumer onClose) {
        return new AvoWebSocketClient(URI.create("ws://localhost/ws"), Map.of(), handler, onOpen, onClose);
    }
}
