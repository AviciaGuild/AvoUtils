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

    private final Object authLock = new Object();
    private volatile String sessionToken = null;
    private CompletableFuture<String> activeAuthFuture = null;

    private AvoAuthService(ModConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static synchronized void initialize(ModConfig config) {
        if (instance == null) {
            instance = new AvoAuthService(config);
        }
    }

    public static synchronized AvoAuthService getInstance() {
        if (instance == null) {
            instance = new AvoAuthService(ModConfig.load());
        }
        return instance;
    }

    public void invalidateToken() {
        synchronized (authLock) {
            sessionToken = null;
        }
    }

    public CompletableFuture<String> getSessionToken() {
        synchronized (authLock) {
            if (sessionToken != null) {
                return CompletableFuture.completedFuture(sessionToken);
            }
            if (activeAuthFuture != null) {
                return activeAuthFuture;
            }
            activeAuthFuture = fetchSessionTokenAsync().thenApply(token -> {
                synchronized (authLock) {
                    sessionToken = token;
                    activeAuthFuture = null;
                }
                return token;
            }).exceptionally(ex -> {
                synchronized (authLock) {
                    activeAuthFuture = null;
                }
                throw new RuntimeException("Authentication failed: " + ex.getMessage(), ex);
            });
            return activeAuthFuture;
        }
    }

    private CompletableFuture<String> fetchSessionTokenAsync() {
        Session session = MinecraftClient.getInstance().getSession();
        UUID uuid = session.getUuidOrNull();
        if (uuid == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not logged into Minecraft."));
        }
        String uuidStr = uuid.toString().replace("-", "");

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
                            MinecraftClient.getInstance().getApiServices().sessionService().joinServer(
                                    session.getUuidOrNull(),
                                    session.getAccessToken(),
                                    challenge
                            );
                        } catch (Exception e) {
                            throw new RuntimeException("Mojang authentication failed: " + e.getMessage(), e);
                        }
                    }).thenApply(v -> challenge);
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
                        throw new RuntimeException("Login failed (HTTP " + response.statusCode() + ")");
                    }
                    AuthApiResponse apiResp = GSON.fromJson(response.body(), AuthApiResponse.class);
                    if (apiResp == null || apiResp.token == null) {
                        throw new RuntimeException("Invalid login response: missing token");
                    }
                    return apiResp.token;
                });
    }

    private static class AuthApiResponse {
        public String challenge;
        public String token;
    }
}
