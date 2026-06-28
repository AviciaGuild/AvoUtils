package info.avicia.avoutils.features.partyfinder.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP client for the pfinder API
 */
public class PartyFinderClient {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final ModConfig config;
    private final HttpClient httpClient;

    private final Object authLock = new Object();
    private volatile String sessionToken = null;
    private CompletableFuture<String> activeAuthFuture = null;

    public PartyFinderClient(ModConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ── Auth ──────────────────────────────────────────────────────────────

    private HttpRequest.Builder baseRequest(String path, String token) {
        String url = config.apiBaseUrl + path;
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15));
    }

    private CompletableFuture<String> getSessionToken() {
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
                    ApiResponse apiResp = GSON.fromJson(response.body(), ApiResponse.class);
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
                    ApiResponse apiResp = GSON.fromJson(response.body(), ApiResponse.class);
                    if (apiResp == null || apiResp.token == null) {
                        throw new RuntimeException("Invalid login response: missing token");
                    }
                    return apiResp.token;
                });
    }

    private HttpRequest buildRequest(String path, String token, String method, HttpRequest.BodyPublisher bodyPublisher) {
        HttpRequest.Builder builder = baseRequest(path, token);
        if (method.equals("GET")) {
            builder.GET();
        } else if (method.equals("DELETE")) {
            builder.DELETE();
        } else {
            builder.method(method, bodyPublisher);
        }
        return builder.build();
    }

    private <T> CompletableFuture<T> executeAuthenticated(
            String path,
            String method,
            HttpRequest.BodyPublisher bodyPublisher,
            java.util.function.Function<HttpResponse<String>, T> parser
    ) {
        return getSessionToken().thenCompose(token -> {
            HttpRequest request = buildRequest(path, token, method, bodyPublisher);
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenCompose(response -> {
                        if (response.statusCode() == 401) {
                            // Token is invalid/expired; reset token and retry once
                            synchronized (authLock) {
                                sessionToken = null;
                            }
                            return getSessionToken().thenCompose(newToken -> {
                                HttpRequest retryRequest = buildRequest(path, newToken, method, bodyPublisher);
                                return httpClient.sendAsync(retryRequest, HttpResponse.BodyHandlers.ofString());
                            });
                        }
                        return CompletableFuture.completedFuture(response);
                    });
        }).thenApply(parser);
    }

    private ApiResponse parseResponse(HttpResponse<String> response) {
        try {
            return GSON.fromJson(response.body(), ApiResponse.class);
        } catch (Exception e) {
            ApiResponse err = new ApiResponse();
            err.ok = false;
            err.error = "Failed to parse response (HTTP " + response.statusCode() + ")";
            return err;
        }
    }

    private void throwOnError(HttpResponse<String> response, String actionName, String defaultMessagePrefix) {
        if (response.statusCode() != 200) {
            AvoUtilsMod.LOGGER.warn("{} failed: HTTP {}", actionName, response.statusCode());
            String errorMessage = defaultMessagePrefix + " (HTTP " + response.statusCode() + ").";
            try {
                ApiResponse apiResp = GSON.fromJson(response.body(), ApiResponse.class);
                if (apiResp != null && apiResp.error != null) {
                    errorMessage = apiResp.error;
                }
            } catch (Exception ignored) {
            }
            throw new RuntimeException(errorMessage);
        }
    }

    // ── API Calls ─────────────────────────────────────────────────────────

    /**
     * GET /api/parties
     */
    public CompletableFuture<List<PartyData>> listParties() {
        return executeAuthenticated("/api/parties", "GET", null, response -> {
            throwOnError(response, "listParties", "Failed to fetch parties");
            ApiResponse apiResp = GSON.fromJson(response.body(), ApiResponse.class);
            return apiResp.parties != null ? apiResp.parties : List.of();
        });
    }

    /**
     * GET /api/parties/{id}
     */
    public CompletableFuture<PartyData> getParty(long partyId) {
        return executeAuthenticated("/api/parties/" + partyId, "GET", null, response -> {
            throwOnError(response, "getParty", "Failed to fetch party");
            ApiResponse apiResp = GSON.fromJson(response.body(), ApiResponse.class);
            if (apiResp == null || apiResp.party == null) {
                throw new RuntimeException("Party not found.");
            }
            return apiResp.party;
        });
    }

    /**
     * POST /api/parties
     */
    public CompletableFuture<ApiResponse> createParty(String role, List<String> activities,
                                                       String region, String note, int reservedSlots, boolean ping) {
        JsonObject body = new JsonObject();
        body.addProperty("role", role);
        body.add("activities", GSON.toJsonTree(activities));
        if (region != null) body.addProperty("region", region);
        if (note != null) body.addProperty("note", note);
        body.addProperty("reserved_slots", reservedSlots);
        body.addProperty("ping", ping);

        return executeAuthenticated("/api/parties", "POST", HttpRequest.BodyPublishers.ofString(GSON.toJson(body)), this::parseResponse);
    }

    /**
     * POST /api/parties/{id}/edit
     */
    public CompletableFuture<ApiResponse> editParty(long partyId, List<String> activities,
                                                     String region, String note, boolean ping) {
        JsonObject body = new JsonObject();
        body.add("activities", GSON.toJsonTree(activities));
        if (region != null) body.addProperty("region", region);
        else body.addProperty("region", "");
        if (note != null) body.addProperty("note", note);
        else body.addProperty("note", "");
        body.addProperty("ping", ping);

        return executeAuthenticated("/api/parties/" + partyId + "/edit", "POST", HttpRequest.BodyPublishers.ofString(GSON.toJson(body)), this::parseResponse);
    }

    /**
     * POST /api/parties/{id}/join
     */
    public CompletableFuture<ApiResponse> joinParty(long partyId, String role) {
        JsonObject body = new JsonObject();
        body.addProperty("role", role);

        return executeAuthenticated("/api/parties/" + partyId + "/join", "POST", HttpRequest.BodyPublishers.ofString(GSON.toJson(body)), this::parseResponse);
    }

    /**
     * POST /api/parties/{id}/leave
     */
    public CompletableFuture<ApiResponse> leaveParty(long partyId) {
        return executeAuthenticated("/api/parties/" + partyId + "/leave", "POST", HttpRequest.BodyPublishers.noBody(), this::parseResponse);
    }

    /**
     * DELETE /api/parties/{id}
     */
    public CompletableFuture<ApiResponse> disbandParty(long partyId) {
        return executeAuthenticated("/api/parties/" + partyId, "DELETE", null, this::parseResponse);
    }

    /**
     * POST /api/parties/{id}/kick
     */
    public CompletableFuture<ApiResponse> kickMember(long partyId, String targetName) {
        JsonObject body = new JsonObject();
        body.addProperty("target", targetName);

        return executeAuthenticated("/api/parties/" + partyId + "/kick", "POST", HttpRequest.BodyPublishers.ofString(GSON.toJson(body)), this::parseResponse);
    }

    /**
     * POST /api/parties/{id}/reserve
     */
    public CompletableFuture<ApiResponse> reserveSlot(long partyId, String name, String role) {
        JsonObject body = new JsonObject();
        if (name != null) body.addProperty("name", name);
        if (role != null) body.addProperty("role", role);

        return executeAuthenticated("/api/parties/" + partyId + "/reserve", "POST", HttpRequest.BodyPublishers.ofString(GSON.toJson(body)), this::parseResponse);
    }

    /**
     * GET /api/parties/{id}/members
     */
    public CompletableFuture<List<PartyData.MemberData>> getMembers(long partyId) {
        return executeAuthenticated("/api/parties/" + partyId + "/members", "GET", null, response -> {
            if (response.statusCode() != 200) {
                return List.of();
            }
            ApiResponse apiResp = GSON.fromJson(response.body(), ApiResponse.class);
            return apiResp.members != null ? apiResp.members : List.of();
        });
    }

    /**
     * POST /api/parties/{id}/reserve-ingame
     */
    public CompletableFuture<ApiResponse> reserveIngame(long partyId, String minecraftName) {
        JsonObject body = new JsonObject();
        body.addProperty("minecraft_name", minecraftName);

        return executeAuthenticated("/api/parties/" + partyId + "/reserve-ingame", "POST", HttpRequest.BodyPublishers.ofString(GSON.toJson(body)), this::parseResponse);
    }
}
