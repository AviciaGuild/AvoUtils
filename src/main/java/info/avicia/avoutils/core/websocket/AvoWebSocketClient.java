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

/**
 * WebSocket client connecting to the AvoUtils API gateway
 */
public class AvoWebSocketClient extends WebSocketClient {
    private static final Gson GSON = new Gson();

    private final BiConsumer<String, JsonObject> eventHandler;
    private final Runnable onOpenCallback;
    private final Runnable onCloseCallback;

    public AvoWebSocketClient(URI serverUri, Map<String, String> httpHeaders,
                              BiConsumer<String, JsonObject> eventHandler,
                              Runnable onOpenCallback, Runnable onCloseCallback) {
        super(serverUri, httpHeaders);
        this.eventHandler = eventHandler;
        this.onOpenCallback = onOpenCallback;
        this.onCloseCallback = onCloseCallback;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        AvoUtilsMod.LOGGER.info("[AvoWebSocket] Connection opened. Status: " + handshakedata.getHttpStatus());
        if (onOpenCallback != null) {
            onOpenCallback.run();
        }
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonObject json = GSON.fromJson(message, JsonObject.class);
            if (json != null && json.has("type")) {
                String type = json.get("type").getAsString();
                eventHandler.accept(type, json);
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoWebSocket] Error processing message: " + message, e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        AvoUtilsMod.LOGGER.info("[AvoWebSocket] Connection closed. Code: " + code + ", Reason: " + reason + ", Remote: " + remote);
        if (reason != null && reason.contains("401")) {
            AvoUtilsMod.LOGGER.warn("[AvoWebSocket] Unauthorized connection attempt (401). Invalidating cached session token.");
            AvoAuthService.getInstance().invalidateToken();
        }
        if (onCloseCallback != null) {
            onCloseCallback.run();
        }
    }

    @Override
    public void onError(Exception ex) {
        AvoUtilsMod.LOGGER.error("[AvoWebSocket] Socket error occurred", ex);
    }

    public void sendEvent(String type, JsonObject payload) {
        if (isOpen()) {
            if (payload == null) {
                payload = new JsonObject();
            }
            payload.addProperty("type", type);
            send(GSON.toJson(payload));
        } else {
            AvoUtilsMod.LOGGER.warn("[AvoWebSocket] Cannot send event. Connection not open: " + type);
        }
    }
}
