package info.avicia.avoutils.features.rewards.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import info.avicia.avoutils.core.api.ApiException;
import info.avicia.avoutils.core.api.AvoApiClient;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.features.rewards.RewardKind;

import java.util.concurrent.CompletableFuture;

/**
 * The bot's rewards API. A 409 from a claim or a lock is a result ({@code ok} false); any other status but 200
 * fails the future with an {@link ApiException}.
 */
public class RewardsClient {
    private static final Gson GSON = AvoApiClient.GSON;

    private final AvoApiClient api;

    public RewardsClient(ModConfig config) {
        this(new AvoApiClient(config));
    }

    RewardsClient(AvoApiClient api) {
        this.api = api;
    }

    /** What each player is owed. */
    public CompletableFuture<RewardsResponse> fetchPending() {
        return send("GET", "/api/rewards/pending", null, false);
    }

    /** Records {@code amount} as handed out; retries of the same claim must reuse its {@code claimId}. */
    public CompletableFuture<RewardsResponse> claim(String uuid, RewardKind kind, long amount, String claimId) {
        JsonObject body = playerBody(uuid, kind);
        body.addProperty("amount", amount);
        body.addProperty("claim_id", claimId);
        return send("POST", "/api/rewards/claim", body, true);
    }

    /** Takes the hand-out lock for a player's rewards of one type; the reply says what's pending now. */
    public CompletableFuture<RewardsResponse> lock(String uuid, RewardKind kind) {
        return send("POST", "/api/rewards/lock", playerBody(uuid, kind), true);
    }

    public CompletableFuture<RewardsResponse> unlock(String uuid, RewardKind kind) {
        return send("POST", "/api/rewards/unlock", playerBody(uuid, kind), false);
    }

    private static JsonObject playerBody(String uuid, RewardKind kind) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", uuid);
        body.addProperty("type", kind.apiName());
        return body;
    }

    private CompletableFuture<RewardsResponse> send(String method, String path, JsonObject body,
                                                    boolean conflictIsResult) {
        return api.send(method, path, body != null ? GSON.toJson(body) : null).thenApply(response -> {
            int status = response.statusCode();
            if (status == 200 || (conflictIsResult && status == 409)) {
                RewardsResponse parsed = GSON.fromJson(response.body(), RewardsResponse.class);
                if (parsed != null) {
                    return parsed;
                }
            }
            String error = AvoApiClient.errorMessage(response.body());
            throw new ApiException(status, error != null ? error : "The bot answered HTTP " + status + ".");
        });
    }
}
