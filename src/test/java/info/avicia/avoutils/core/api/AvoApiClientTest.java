package info.avicia.avoutils.core.api;

import info.avicia.avoutils.BuildConfig;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvoApiClientTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final AvoApiClient client = new AvoApiClient(new ModConfig(), httpClient);

    @Test
    void buildGetRequestAddsAuthHeaderAndPath() {
        HttpRequest request = client.buildRequest("GET", "/api/parties", "token-123", null);

        assertEquals("GET", request.method());
        assertEquals(BuildConfig.API_BASE_URL + "/api/parties", request.uri().toString());
        assertEquals("Bearer token-123", request.headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void buildDeleteRequestUsesDeleteMethod() {
        assertEquals("DELETE", client.buildRequest("DELETE", "/api/parties/1", "t", null).method());
    }

    @Test
    void buildPostRequestSendsTheBodyOrNothing() {
        HttpRequest withBody = client.buildRequest("POST", "/api/parties", "t", "{}");
        HttpRequest withoutBody = client.buildRequest("POST", "/api/parties/1/leave", "t", null);

        assertEquals("POST", withBody.method());
        assertEquals(2, withBody.bodyPublisher().orElseThrow().contentLength());
        assertEquals(0, withoutBody.bodyPublisher().orElseThrow().contentLength());
    }

    @Test
    void sendRenewsTheTokenAndRetriesOnceAfter401() {
        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            when(authService.getSessionToken()).thenReturn(CompletableFuture.completedFuture("old"),
                    CompletableFuture.completedFuture("new"));
            doReturn(CompletableFuture.completedFuture(response(401)), CompletableFuture.completedFuture(response(200)))
                    .when(httpClient).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

            HttpResponse<String> result = client.send("GET", "/api/rewards/pending", null).join();

            assertEquals(200, result.statusCode());
            verify(authService).invalidateToken();
            ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient, times(2)).sendAsync(requests.capture(), any(HttpResponse.BodyHandler.class));
            assertEquals("Bearer new", requests.getAllValues().get(1).headers().firstValue("Authorization").orElseThrow());
        }
    }

    @Test
    void sendReturnsOtherStatusesWithoutRetrying() {
        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            when(authService.getSessionToken()).thenReturn(CompletableFuture.completedFuture("tok"));
            doReturn(CompletableFuture.completedFuture(response(403)))
                    .when(httpClient).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

            assertEquals(403, client.send("POST", "/api/rewards/lock", "{}").join().statusCode());
            verify(authService, never()).invalidateToken();
        }
    }

    @Test
    void errorMessageReadsTheErrorField() {
        assertEquals("Bad token", AvoApiClient.errorMessage("{\"error\":\"Bad token\"}"));
    }

    @Test
    void errorMessageIsNullWithoutAnErrorField() {
        assertNull(AvoApiClient.errorMessage("{\"message\":\"x\"}"));
        assertNull(AvoApiClient.errorMessage("{\"error\":null}"));
        assertNull(AvoApiClient.errorMessage("not json"));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        return response;
    }
}
