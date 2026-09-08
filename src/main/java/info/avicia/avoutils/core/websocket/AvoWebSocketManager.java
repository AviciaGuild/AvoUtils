package info.avicia.avoutils.core.websocket;

import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.auth.AvoAuthService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import info.avicia.avoutils.core.util.ClientVersion;
import info.avicia.avoutils.core.util.WynnPillUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private volatile boolean versionUnsupported = false;
    private volatile long lastConnectAttempt = 0;
    private int tickCounter = 0;
    private volatile int consecutiveFailures = 0;
    private static final int TICK_INTERVAL = 20;
    private static final long BASE_RETRY_MS = 5_000;
    private static final long MAX_RETRY_MS = 120_000;

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
            throw new IllegalStateException("AvoWebSocketManager not initialized.");
        }
        return instance;
    }

    private void setupLifecycle() {
        // Tick connection check when player is in-game
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (++tickCounter >= TICK_INTERVAL) {
                tickCounter = 0;
                if (MinecraftClient.getInstance().player != null) {
                    tickConnection();
                }
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
            AvoUtilsMod.LOGGER.warn("[AvoUtils] [WebSocket] Cannot send event. Socket not open: {}", eventType);
        }
    }

    public boolean isConnected() {
        AvoWebSocketClient activeClient = client;
        return activeClient != null && activeClient.isOpen();
    }

    private void tickConnection() {
        if (versionUnsupported) {
            return;
        }

        // Connect if any registered feature demands a connection
        if (connectionDemands.values().stream().noneMatch(BooleanSupplier::getAsBoolean)) {
            disconnect();
            return;
        }

        AvoWebSocketClient activeClient = client;
        if (activeClient != null && activeClient.isOpen()) {
            return;
        }
        if (isConnecting.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        long baseBackoff = baseBackoffForFailures(consecutiveFailures);
        long jitter = (long)(ThreadLocalRandom.current().nextDouble() * baseBackoff * 0.3);
        long backoff = baseBackoff + jitter;
        if (now - lastConnectAttempt < backoff) {
            return;
        }

        triggerConnect();
    }

    private void triggerConnect() {
        if (!isConnecting.compareAndSet(false, true)) {
            return;
        }
        lastConnectAttempt = System.currentTimeMillis();

        AvoUtilsMod.LOGGER.info("[AvoUtils] [WebSocket] Requesting authentication token...");
        AvoAuthService.getInstance().getSessionToken().whenComplete((token, throwable) -> {
            if (throwable != null) {
                String msg = throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage();
                AvoUtilsMod.LOGGER.error("[AvoUtils] [WebSocket] Failed to retrieve session token: {}", msg);
                isConnecting.set(false);
                consecutiveFailures++;
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
                try {
                    client.close();
                } catch (Exception e) {
                    AvoUtilsMod.LOGGER.warn("[AvoUtils] [WebSocket] Error closing previous client", e);
                }
            }

            URI wsUri = wsUriFor(config.apiBaseUrl);

            AvoUtilsMod.LOGGER.info("[AvoUtils] [WebSocket] Connecting to: {}", wsUri);

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + token);
            headers.put(ClientVersion.MOD_VERSION_HEADER, ClientVersion.resolveInstalledVersion());

            client = new AvoWebSocketClient(wsUri, headers,
                    this::handleIncomingEvent,
                    () -> {
                        isConnecting.set(false);
                        consecutiveFailures = 0;
                        AvoUtilsMod.LOGGER.info("[AvoUtils] [WebSocket] Connected successfully.");
                    },
                    code -> {
                        isConnecting.set(false);
                        if (code == 1000 || code == 1001) {
                            consecutiveFailures = 0;
                        } else if (code == AvoWebSocketClient.UNSUPPORTED_VERSION_CLOSE_CODE) {
                            versionUnsupported = true;
                            notifyVersionUnsupported();
                        }
                        AvoUtilsMod.LOGGER.info("[AvoUtils] [WebSocket] Disconnected (code={}).", code);
                    }
            );
            client.connect();
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] [WebSocket] Error connecting", e);
            isConnecting.set(false);
            consecutiveFailures++;
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
                        AvoUtilsMod.LOGGER.error("[AvoUtils] [WebSocket] Error invoking listener for event: {}", type, e);
                    }
                }
            }
        });
    }

    private void notifyVersionUnsupported() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    try {
                        if (mc.player != null) {
                            MutableText warning = WynnPillUtil.createPrefixedPill("AvoUtils", true)
                                    .append(Text.literal("WARNING: ").formatted(Formatting.DARK_RED, Formatting.BOLD))
                                    .append(Text.literal("Your AvoUtils mod is outdated! Please update to use online features.")
                                            .formatted(Formatting.RED));
                            mc.player.sendMessage(warning, false);
                        }
                    } catch (Throwable ignored) {
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean isVersionUnsupported() {
        return versionUnsupported;
    }

    public void setVersionUnsupported(boolean unsupported) {
        this.versionUnsupported = unsupported;
    }

    public void disconnect() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                AvoUtilsMod.LOGGER.error("[AvoUtils] [WebSocket] Error during disconnect", e);
            }
            client = null;
        }
        isConnecting.set(false);
        consecutiveFailures = 0;
    }

    // Builds the gateway WebSocket URI from the HTTP API base URL
    static URI wsUriFor(String apiBaseUrl) throws Exception {
        URI base = new URI(apiBaseUrl);
        String wsScheme = "https".equalsIgnoreCase(base.getScheme()) ? "wss" : "ws";
        return new URI(wsScheme, null, base.getHost(), base.getPort(), "/ws", null, null);
    }

    // Exponential backoff base (before jitter) for the given consecutive failure count
    static long baseBackoffForFailures(int failures) {
        return Math.min(BASE_RETRY_MS * (1L << Math.min(failures, 5)), MAX_RETRY_MS);
    }
}
