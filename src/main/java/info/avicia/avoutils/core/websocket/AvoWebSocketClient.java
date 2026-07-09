package info.avicia.avoutils.core.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.auth.AvoAuthService;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

/**
 * WebSocket client connecting to the AvoUtils API gateway
 */
public class AvoWebSocketClient extends WebSocketClient {
    private static final Gson GSON = new Gson();
    private static final int MAX_MESSAGE_SIZE = 256 * 1024; // 256 KB
    public static final int AUTH_FAILURE_CLOSE_CODE = 4001;
    private static final int PROTOCOL_ERROR_CLOSE_CODE = 1002;

    private final BiConsumer<String, JsonObject> eventHandler;
    private final Runnable onOpenCallback;
    private final IntConsumer onCloseCallback;

    public AvoWebSocketClient(URI serverUri, Map<String, String> httpHeaders,
                              BiConsumer<String, JsonObject> eventHandler,
                              Runnable onOpenCallback, IntConsumer onCloseCallback) {
        super(serverUri, httpHeaders);
        setConnectionLostTimeout(30);
        this.eventHandler = eventHandler;
        this.onOpenCallback = onOpenCallback;
        this.onCloseCallback = onCloseCallback;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        AvoUtilsMod.LOGGER.info("[AvoWebSocket] Connection opened. Status: {}", handshakedata.getHttpStatus());
        if (onOpenCallback != null) {
            onOpenCallback.run();
        }
    }

    @Override
    public void onMessage(String message) {
        if (message.length() > MAX_MESSAGE_SIZE) {
            AvoUtilsMod.LOGGER.warn("[AvoWebSocket] Dropping oversized message: {} bytes", message.length());
            return;
        }
        try {
            JsonObject json = GSON.fromJson(message, JsonObject.class);
            if (json != null && json.has("type")) {
                String type = json.get("type").getAsString();
                eventHandler.accept(type, json);
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoWebSocket] Error processing incoming message", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        AvoUtilsMod.LOGGER.info("[AvoWebSocket] Connection closed. Code: {}, Reason: {}, Remote: {}", code, reason, remote);
        if (code == AUTH_FAILURE_CLOSE_CODE || code == PROTOCOL_ERROR_CLOSE_CODE) {
            AvoUtilsMod.LOGGER.warn("[AvoWebSocket] Auth failure or protocol error (code={}). Invalidating session token.", code);
            AvoAuthService.getInstance().invalidateToken();
        }
        if (onCloseCallback != null) {
            onCloseCallback.accept(code);
        }
    }

    @Override
    public void onError(Exception ex) {
        AvoUtilsMod.LOGGER.error("[AvoWebSocket] Socket error occurred", ex);
    }

    public void sendEvent(String type, JsonObject payload) {
        if (isOpen()) {
            JsonObject envelope = payload != null ? payload.deepCopy() : new JsonObject();
            envelope.addProperty("type", type);
            send(GSON.toJson(envelope));
        } else {
            AvoUtilsMod.LOGGER.warn("[AvoWebSocket] Cannot send event. Connection not open: {}", type);
        }
    }
}
