package info.avicia.avoutils.core.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Authenticated requests to the Avicia API: the session token from {@link AvoAuthService} is sent as a Bearer token,
 * and renewed and retried once when the API answers 401. Replies are returned as they are; callers check the status.
 */
public class AvoApiClient {
    /** The API's JSON uses snake_case field names. */
    public static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final ModConfig config;
    private final HttpClient httpClient;

    public AvoApiClient(ModConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public AvoApiClient(ModConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    /** Sends {@code jsonBody} (nothing if null) to {@code path}. */
    public CompletableFuture<HttpResponse<String>> send(String method, String path, String jsonBody) {
        AvoAuthService auth = AvoAuthService.getInstance();
        return auth.getSessionToken()
                .thenCompose(token -> sendWithToken(method, path, jsonBody, token))
                .thenCompose(response -> {
                    if (response.statusCode() != 401) {
                        return CompletableFuture.completedFuture(response);
                    }
                    // Token is invalid/expired; reset token and retry once
                    auth.invalidateToken();
                    return auth.getSessionToken().thenCompose(newToken -> sendWithToken(method, path, jsonBody, newToken));
                });
    }

    private CompletableFuture<HttpResponse<String>> sendWithToken(String method, String path, String jsonBody,
                                                                 String token) {
        return httpClient.sendAsync(buildRequest(method, path, token, jsonBody), HttpResponse.BodyHandlers.ofString());
    }

    HttpRequest buildRequest(String method, String path, String token, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiBaseUrl() + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15));
        switch (method) {
            case "GET" -> builder.GET();
            case "DELETE" -> builder.DELETE();
            default -> builder.method(method, jsonBody != null
                    ? HttpRequest.BodyPublishers.ofString(jsonBody)
                    : HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    /** The {@code error} field of a JSON reply, or null if it has none. */
    public static String errorMessage(String responseBody) {
        try {
            JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
            if (json != null && json.has("error")) {
                return json.get("error").getAsString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
