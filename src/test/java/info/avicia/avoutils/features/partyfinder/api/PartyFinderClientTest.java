package info.avicia.avoutils.features.partyfinder.api;

import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class PartyFinderClientTest {

    private final PartyFinderClient client = new PartyFinderClient(new ModConfig());

    @Test
    void buildGetRequestAddsAuthHeaderAndPath() throws Exception {
        HttpRequest request = buildRequest("/api/parties", "token-123", "GET", null);

        assertEquals("GET", request.method());
        assertEquals("https://auth.avicia.info:8443/api/parties", request.uri().toString());
        assertEquals("Bearer token-123", request.headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void buildDeleteRequestUsesDeleteMethod() throws Exception {
        HttpRequest request = buildRequest("/api/parties/1", "t", "DELETE", null);
        assertEquals("DELETE", request.method());
    }

    @Test
    void buildPostRequestIncludesBody() throws Exception {
        HttpRequest request = buildRequest("/api/parties", "t", "POST", HttpRequest.BodyPublishers.ofString("{}"));

        assertEquals("POST", request.method());
        assertTrue(request.bodyPublisher().isPresent());
    }

    @Test
    void parseResponseParsesValidJson() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"ok\":true,\"error\":null}");

        ApiResponse parsed = parseResponse(response);
        assertTrue(parsed.ok);
    }

    @Test
    void parseResponseHandlesMalformedJson() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("not json");

        ApiResponse parsed = parseResponse(response);
        assertFalse(parsed.ok);
        assertTrue(parsed.error.contains("Failed to parse response"));
    }

    @Test
    void throwOnErrorThrowsWithBackendErrorMessage() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("{\"error\":\"boom\"}");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> throwOnError(response, "listParties", "Failed to fetch parties"));
        assertEquals("boom", exception.getMessage());
    }

    @Test
    void throwOnErrorDoesNotThrowFor200() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{}");

        throwOnError(response, "listParties", "Failed");
    }

    @Test
    void listPartiesUsesInjectedHttpClient() {
        HttpClient httpClient = mock(HttpClient.class);
        PartyFinderClient client = new PartyFinderClient(new ModConfig(), httpClient);

        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            when(authService.getSessionToken()).thenReturn(CompletableFuture.completedFuture("tok"));

            @SuppressWarnings("unchecked")
            HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn("{\"parties\":[{\"party_id\":1,\"leader_name\":\"Steve\"}]}");

            doReturn(CompletableFuture.completedFuture(response))
                    .when(httpClient).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

            List<PartyData> parties = client.listParties().join();

            assertEquals(1, parties.size());
            assertEquals(1L, parties.get(0).partyId);
        }
    }

    private HttpRequest buildRequest(String path, String token, String method,
                                     HttpRequest.BodyPublisher bodyPublisher) throws Exception {
        Method methodObj = PartyFinderClient.class.getDeclaredMethod(
                "buildRequest", String.class, String.class, String.class, HttpRequest.BodyPublisher.class);
        methodObj.setAccessible(true);
        return (HttpRequest) methodObj.invoke(client, path, token, method, bodyPublisher);
    }

    private ApiResponse parseResponse(HttpResponse<String> response) throws Exception {
        Method method = PartyFinderClient.class.getDeclaredMethod("parseResponse", HttpResponse.class);
        method.setAccessible(true);
        return (ApiResponse) method.invoke(client, response);
    }

    private void throwOnError(HttpResponse<String> response, String actionName, String prefix) throws Exception {
        Method method = PartyFinderClient.class.getDeclaredMethod(
                "throwOnError", HttpResponse.class, String.class, String.class);
        method.setAccessible(true);
        try {
            method.invoke(client, response, actionName, prefix);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }
}
