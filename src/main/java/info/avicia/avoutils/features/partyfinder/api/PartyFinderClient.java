package info.avicia.avoutils.features.partyfinder.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.api.ApiException;
import info.avicia.avoutils.core.api.AvoApiClient;
import info.avicia.avoutils.core.config.ModConfig;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * HTTP client for the pfinder API
 */
public class PartyFinderClient {
    private static final Gson GSON = AvoApiClient.GSON;

    private final AvoApiClient api;

    public PartyFinderClient(ModConfig config) {
        this(new AvoApiClient(config));
    }

    PartyFinderClient(ModConfig config, HttpClient httpClient) {
        this(new AvoApiClient(config, httpClient));
    }

    private PartyFinderClient(AvoApiClient api) {
        this.api = api;
    }

    private <T> CompletableFuture<T> executeAuthenticated(String path, String method, JsonObject body,
                                                          Function<HttpResponse<String>, T> parser) {
        return api.send(method, path, body != null ? GSON.toJson(body) : null).thenApply(parser);
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
            AvoUtilsMod.LOGGER.warn("[AvoUtils] [PartyFinder] {} failed: HTTP {}", actionName, response.statusCode());
            String error = AvoApiClient.errorMessage(response.body());
            throw new ApiException(response.statusCode(),
                    error != null ? error : defaultMessagePrefix + " (HTTP " + response.statusCode() + ").");
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
            return (apiResp != null && apiResp.parties != null) ? apiResp.parties : List.of();
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

        return executeAuthenticated("/api/parties", "POST", body, this::parseResponse);
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

        return executeAuthenticated("/api/parties/" + partyId + "/edit", "POST", body, this::parseResponse);
    }

    /**
     * POST /api/parties/{id}/join
     */
    public CompletableFuture<ApiResponse> joinParty(long partyId, String role) {
        JsonObject body = new JsonObject();
        body.addProperty("role", role);

        return executeAuthenticated("/api/parties/" + partyId + "/join", "POST", body, this::parseResponse);
    }

    /**
     * POST /api/parties/{id}/leave
     */
    public CompletableFuture<ApiResponse> leaveParty(long partyId) {
        return executeAuthenticated("/api/parties/" + partyId + "/leave", "POST", null, this::parseResponse);
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

        return executeAuthenticated("/api/parties/" + partyId + "/kick", "POST", body, this::parseResponse);
    }

    /**
     * POST /api/parties/{id}/reserve
     */
    public CompletableFuture<ApiResponse> reserveSlot(long partyId, String name, String role) {
        JsonObject body = new JsonObject();
        if (name != null) body.addProperty("name", name);
        if (role != null) body.addProperty("role", role);

        return executeAuthenticated("/api/parties/" + partyId + "/reserve", "POST", body, this::parseResponse);
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

        return executeAuthenticated("/api/parties/" + partyId + "/reserve-ingame", "POST", body, this::parseResponse);
    }
}
