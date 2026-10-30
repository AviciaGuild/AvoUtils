package info.avicia.avoutils.features.rewards.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import info.avicia.avoutils.core.api.ApiException;
import info.avicia.avoutils.core.api.AvoApiClient;
import info.avicia.avoutils.features.rewards.RewardKind;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RewardsClientTest {

    private final AvoApiClient api = mock(AvoApiClient.class);
    private final RewardsClient client = new RewardsClient(api);

    private void reply(int status, String body) {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(api.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(response));
    }

    private JsonObject sentBody(String path) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(api).send(eq("POST"), eq(path), body.capture());
        return JsonParser.parseString(body.getValue()).getAsJsonObject();
    }

    private static ApiException failure(CompletableFuture<?> future) {
        CompletionException error = assertThrows(CompletionException.class, future::join);
        return (ApiException) error.getCause();
    }

    @Test
    void fetchesWhatEachPlayerIsOwed() {
        reply(200, "{\"ok\":true,\"players\":[{\"uuid\":\"u1\",\"username\":\"Steve\",\"in_guild\":true,"
                + "\"rewards\":{\"le\":11,\"tome\":2}}]}");

        RewardsResponse response = client.fetchPending().join();

        verify(api).send(eq("GET"), eq("/api/rewards/pending"), isNull());
        PlayerRewards steve = response.players.get(0);
        assertEquals("Steve", steve.username);
        assertTrue(steve.inGuild);
        assertEquals(11L, steve.rewards.get("le"));
    }

    @Test
    void claimSendsTheAmountAndTheClaimId() {
        reply(200, "{\"ok\":true,\"claimed\":3,\"pending\":2}");

        RewardsResponse response = client.claim("u1", RewardKind.TOME, 3, "claim-1").join();

        assertEquals(2L, response.pending);
        JsonObject body = sentBody("/api/rewards/claim");
        assertEquals("u1", body.get("uuid").getAsString());
        assertEquals("tome", body.get("type").getAsString());
        assertEquals(3, body.get("amount").getAsLong());
        assertEquals("claim-1", body.get("claim_id").getAsString());
    }

    @Test
    void aClaimForMoreThanIsPendingIsAResult() {
        reply(409, "{\"ok\":false,\"pending\":1,\"error\":\"Nothing was recorded\"}");

        RewardsResponse response = client.claim("u1", RewardKind.LE, 3, "claim-1").join();

        assertFalse(response.ok);
        assertEquals(1L, response.pending);
    }

    @Test
    void aLockHeldByAnotherChiefIsAResult() {
        reply(409, "{\"ok\":false,\"locked_by\":\"ChiefA\",\"error\":\"ChiefA is already handing these out.\"}");

        RewardsResponse response = client.lock("u1", RewardKind.ASPECT).join();

        assertFalse(response.ok);
        assertEquals("ChiefA", response.lockedBy);
        assertEquals("aspect", sentBody("/api/rewards/lock").get("type").getAsString());
    }

    @Test
    void otherStatusesFailWithTheBotsError() {
        reply(403, "{\"ok\":false,\"error\":\"Only guild management (Aedile+) can hand out rewards.\"}");

        ApiException error = failure(client.fetchPending());

        assertEquals(403, error.status());
        assertEquals("Only guild management (Aedile+) can hand out rewards.", error.getMessage());
    }

    @Test
    void aBotWithoutTheLockRouteAnswers404() {
        reply(404, "404: Not Found");

        ApiException error = failure(client.lock("u1", RewardKind.TOME));

        assertEquals(404, error.status());
        assertEquals("The bot answered HTTP 404.", error.getMessage());
    }

    @Test
    void anUnlockConflictIsNotExpected() {
        reply(409, "{\"ok\":false,\"error\":\"?\"}");

        assertEquals(409, failure(client.unlock("u1", RewardKind.TOME)).status());
    }

    @Test
    void claimSendsExactFreeformRewardType() {
        reply(200, "{\"ok\":true,\"claimed\":1,\"pending\":0}");

        RewardsResponse response = client.claim("u1", RewardKind.fromApiName("Shiny Warp"), 1, "claim-9").join();

        assertTrue(response.ok);
        JsonObject body = sentBody("/api/rewards/claim");
        assertEquals("u1", body.get("uuid").getAsString());
        assertEquals("Shiny Warp", body.get("type").getAsString());
        assertEquals(1, body.get("amount").getAsLong());
    }
}
