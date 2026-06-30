package info.avicia.avoutils.core.websocket;

import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.auth.AvoAuthService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Shared manager that coordinates the mod gateway WebSocket connection and
 * multiplexes events to subscribing features
 */
public class AvoWebSocketManager {
    private static AvoWebSocketManager instance;

    private final ModConfig config;
    private final Map<String, List<Consumer<JsonObject>>> listeners = new ConcurrentHashMap<>();
    private final Map<String, BooleanSupplier> connectionDemands = new ConcurrentHashMap<>();

    private volatile AvoWebSocketClient client;
    private volatile boolean isConnecting = false;
    private volatile long lastConnectAttempt = 0;

    private AvoWebSocketManager(ModConfig config) {
        this.config = config;
    }

    public static synchronized void initialize(ModConfig config) {
        if (instance == null) {
            instance = new AvoWebSocketManager(config);
            instance.setupLifecycle();
        }
    }

    public static synchronized AvoWebSocketManager getInstance() {
        if (instance == null) {
            instance = new AvoWebSocketManager(ModConfig.load());
            instance.setupLifecycle();
        }
        return instance;
    }

    private void setupLifecycle() {
        // Tick connection check when player is in-game
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (MinecraftClient.getInstance().player != null) {
                tickConnection();
            }
        });

        // Close socket resources on client exit or server disconnect
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            disconnect();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            disconnect();
        });
    }

    public void registerConnectionDemand(String featureKey, BooleanSupplier demandCheck) {
        connectionDemands.put(featureKey, demandCheck);
    }

    public void unregisterConnectionDemand(String featureKey) {
        connectionDemands.remove(featureKey);
    }

    public void registerListener(String eventType, Consumer<JsonObject> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void unregisterListener(String eventType, Consumer<JsonObject> listener) {
        List<Consumer<JsonObject>> list = listeners.get(eventType);
        if (list != null) {
            list.remove(listener);
        }
    }

    public void sendEvent(String eventType, JsonObject payload) {
        AvoWebSocketClient activeClient = client;
        if (activeClient != null && activeClient.isOpen()) {
            activeClient.sendEvent(eventType, payload);
        } else {
            AvoUtilsMod.LOGGER.warn("[AvoWebSocket] Cannot send event. Socket not open: " + eventType);
        }
    }

    public boolean isConnected() {
        AvoWebSocketClient activeClient = client;
        return activeClient != null && activeClient.isOpen();
    }

    private void tickConnection() {
        // Connect if any registered feature demands a connection
        boolean anyDemand = false;
        for (BooleanSupplier demand : connectionDemands.values()) {
            if (demand.getAsBoolean()) {
                anyDemand = true;
                break;
            }
        }

        if (!anyDemand) {
            disconnect();
            return;
        }

        AvoWebSocketClient activeClient = client;
        if (activeClient != null && activeClient.isOpen()) {
            return;
        }
        if (isConnecting) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastConnectAttempt < 10000) { // Retry every 10 seconds
            return;
        }

        triggerConnect();
    }

    private void triggerConnect() {
        lastConnectAttempt = System.currentTimeMillis();
        isConnecting = true;

        AvoUtilsMod.LOGGER.info("[AvoWebSocket] Requesting authentication token...");
        AvoAuthService.getInstance().getSessionToken().whenComplete((token, throwable) -> {
            if (throwable != null) {
                AvoUtilsMod.LOGGER.error("[AvoWebSocket] Failed to retrieve session token", throwable);
                isConnecting = false;
                return;
            }

            MinecraftClient.getInstance().execute(() -> {
                connectWithToken(token);
            });
        });
    }

    private void connectWithToken(String token) {
        try {
            if (client != null) {
                client.close();
            }

            URI base = new URI(config.apiBaseUrl);
            String wsScheme = "https".equalsIgnoreCase(base.getScheme()) ? "wss" : "ws";
            URI wsUri = new URI(wsScheme, null, base.getHost(), base.getPort(), "/ws", null, null);

            AvoUtilsMod.LOGGER.info("[AvoWebSocket] Connecting to: " + wsUri);

            Map<String, String> headers = new java.util.HashMap<>();
            headers.put("Authorization", "Bearer " + token);

            client = new AvoWebSocketClient(wsUri, headers,
                    this::handleIncomingEvent,
                    () -> {
                        isConnecting = false;
                        AvoUtilsMod.LOGGER.info("[AvoWebSocket] Connected successfully.");
                    },
                    () -> {
                        isConnecting = false;
                        AvoUtilsMod.LOGGER.info("[AvoWebSocket] Disconnected.");
                    }
            );
            client.connect();
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoWebSocket] Error connecting", e);
            isConnecting = false;
        }
    }

    private void handleIncomingEvent(String type, JsonObject json) {
        MinecraftClient.getInstance().execute(() -> {
            List<Consumer<JsonObject>> list = listeners.get(type);
            if (list != null) {
                for (Consumer<JsonObject> listener : list) {
                    try {
                        listener.accept(json);
                    } catch (Exception e) {
                        AvoUtilsMod.LOGGER.error("[AvoWebSocket] Error invoking listener for event: " + type, e);
                    }
                }
            }
        });
    }

    public void disconnect() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                AvoUtilsMod.LOGGER.error("[AvoWebSocket] Error during disconnect", e);
            }
            client = null;
        }
        isConnecting = false;
    }
}
