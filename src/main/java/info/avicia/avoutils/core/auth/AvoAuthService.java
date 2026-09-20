package info.avicia.avoutils.core.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.JsonObject;
import info.avicia.avoutils.core.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared core authentication service. Coordinates Mojang join-server authentication
 * and caches the session token for backend requests.
 */
public class AvoAuthService {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private static AvoAuthService instance;

    private final ModConfig config;
    private final HttpClient httpClient;
    private final ExecutorService authExecutor =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "AvoAuth-Worker");
                t.setDaemon(true);
                return t;
            });

    private static final long TOKEN_TTL_MS = 23 * 60 * 60 * 1000L; // 23 hours

    private final Object authLock = new Object();
    private volatile String sessionToken = null;
    private volatile long sessionTokenExpiry = 0;
    private volatile long invalidatedGeneration = 0;
    private volatile Boolean cachedGuildMember = null;
    private volatile Boolean pendingGuildMember = null;
    private CompletableFuture<String> activeAuthFuture = null;

    private AvoAuthService(ModConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        Runtime.getRuntime().addShutdownHook(new Thread(authExecutor::shutdownNow, "AvoAuth-Shutdown"));
    }

    public static synchronized void initialize(ModConfig config) {
        if (instance == null) {
            instance = new AvoAuthService(config);
        }
    }

    public static synchronized AvoAuthService getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AvoAuthService not initialized.");
        }
        return instance;
    }

    public void invalidateToken() {
        synchronized (authLock) {
            sessionToken = null;
            sessionTokenExpiry = 0;
            cachedGuildMember = null;
            invalidatedGeneration++;
        }
    }

    public Boolean getCachedGuildMember() {
        return cachedGuildMember;
    }

    public void setCachedGuildMember(Boolean guildMember) {
        cachedGuildMember = guildMember;
    }

    /**
     * Whether the linked account is currently a member of the Avicia guild.
     */
    public boolean isGuildMember() {
        return cachedGuildMember != null && cachedGuildMember;
    }

    /**
     * Resolves guild membership status, returning immediately if cached,
     * or fetching a session token asynchronously to check membership.
     */
    public CompletableFuture<Boolean> resolveGuildMembership() {
        synchronized (authLock) {
            if (cachedGuildMember != null) {
                return CompletableFuture.completedFuture(cachedGuildMember);
            }
        }
        return getSessionToken()
                .thenApply(token -> isGuildMember())
                .exceptionally(ex -> false);
    }

    /**
     * Executes the appropriate callback based on guild membership.
     * If cached, runs immediately on the calling thread.
     * If not cached, asynchronously resolves membership and dispatches the callback
     * on the Minecraft client thread (if available) or the async completion thread.
     */
    public void runIfGuildMember(Runnable onAuthorized, Runnable onDenied) {
        if (isGuildMember()) {
            if (onAuthorized != null) {
                onAuthorized.run();
            }
            return;
        }
        if (getCachedGuildMember() != null) {
            if (onDenied != null) {
                onDenied.run();
            }
            return;
        }
        CompletableFuture<Boolean> future = resolveGuildMembership();
        future.thenAccept(isMember -> {
            Runnable action = Boolean.TRUE.equals(isMember) ? onAuthorized : onDenied;
            if (action == null) {
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.execute(action);
            } else {
                action.run();
            }
        });
    }

    public CompletableFuture<String> getSessionToken() {
        synchronized (authLock) {
            if (sessionToken != null && System.currentTimeMillis() < sessionTokenExpiry) {
                return CompletableFuture.completedFuture(sessionToken);
            }
            if (sessionToken != null) {
                sessionToken = null;
                sessionTokenExpiry = 0;
            }
            if (activeAuthFuture != null) {
                return activeAuthFuture;
            }
            long genAtStart = invalidatedGeneration;
            activeAuthFuture = fetchSessionTokenAsync().thenApply(token -> {
                synchronized (authLock) {
                    // Discard token if invalidateToken() was called during auth
                    if (genAtStart == invalidatedGeneration) {
                        sessionToken = token;
                        sessionTokenExpiry = System.currentTimeMillis() + TOKEN_TTL_MS;
                        cachedGuildMember = pendingGuildMember;
                    }
                    pendingGuildMember = null;
                    activeAuthFuture = null;
                }
                return token;
            }).exceptionally(ex -> {
                synchronized (authLock) {
                    activeAuthFuture = null;
                }
                Throwable cause = ex.getCause();
                String msg = cause != null ? cause.getMessage() : ex.getMessage();
                throw new RuntimeException(msg, ex);
            });
            return activeAuthFuture;
        }
    }

    private CompletableFuture<String> fetchSessionTokenAsync() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Session session = mc != null ? mc.getSession() : null;
        if (session == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not logged into Minecraft."));
        }
        UUID uuid = session.getUuidOrNull();
        if (uuid == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not logged into Minecraft."));
        }
        String uuidStr = uuid.toString();

        // Get challenge
        HttpRequest challengeReq = HttpRequest.newBuilder()
                .uri(URI.create(config.apiBaseUrl + "/api/auth/challenge?uuid=" + uuidStr))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        return httpClient.sendAsync(challengeReq, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Failed to get challenge (HTTP " + response.statusCode() + ")");
                    }
                    AuthApiResponse apiResp = GSON.fromJson(response.body(), AuthApiResponse.class);
                    if (apiResp == null || apiResp.challenge == null) {
                        throw new RuntimeException("Invalid challenge response");
                    }
                    return CompletableFuture.completedFuture(apiResp.challenge);
                })
                .thenCompose(challenge -> {
                    // Join server via Mojang SessionService
                    return CompletableFuture.runAsync(() -> {
                        try {
                            MinecraftClient client = MinecraftClient.getInstance();
                            if (client == null || client.getApiServices() == null || client.getApiServices().sessionService() == null) {
                                throw new IllegalStateException("Minecraft session service unavailable.");
                            }
                            client.getApiServices().sessionService().joinServer(
                                    session.getUuidOrNull(),
                                    session.getAccessToken(),
                                    challenge
                            );
                        } catch (Exception e) {
                            throw new RuntimeException("Mojang authentication failed: " + e.getMessage(), e);
                        }
                    }, authExecutor).thenApply(v -> challenge);
                })
                .thenCompose(challenge -> {
                    // Post to login
                    JsonObject body = new JsonObject();
                    body.addProperty("uuid", uuidStr);
                    body.addProperty("username", session.getUsername());
                    body.addProperty("challenge", challenge);

                    HttpRequest loginReq = HttpRequest.newBuilder()
                            .uri(URI.create(config.apiBaseUrl + "/api/auth/login"))
                            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(10))
                            .build();

                    return httpClient.sendAsync(loginReq, HttpResponse.BodyHandlers.ofString());
                })
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        String errorMsg = extractErrorMessage(response.body());
                        throw new RuntimeException(errorMsg != null ? errorMsg : "Login failed (HTTP " + response.statusCode() + ")");
                    }
                    AuthApiResponse apiResp = GSON.fromJson(response.body(), AuthApiResponse.class);
                    if (apiResp == null || apiResp.token == null) {
                        throw new RuntimeException("Invalid login response: missing token");
                    }
                    pendingGuildMember = apiResp.guildMember;
                    return apiResp.token;
                });
    }

    private static String extractErrorMessage(String responseBody) {
        try {
            JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
            if (json != null && json.has("error")) {
                return json.get("error").getAsString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static class AuthApiResponse {
        public String challenge;
        public String token;
        public Boolean guildMember;
    }
}
