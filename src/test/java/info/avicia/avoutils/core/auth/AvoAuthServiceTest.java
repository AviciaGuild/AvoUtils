package info.avicia.avoutils.core.auth;

import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.testutil.TestReflection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvoAuthServiceTest {

    @Test
    void cachedGuildMemberTracksMembership() {
        AvoAuthService.initialize(new ModConfig());
        AvoAuthService service = AvoAuthService.getInstance();

        assertFalse(service.isGuildMember());
        service.setCachedGuildMember(true);

        assertTrue(service.isGuildMember());
        assertTrue(service.getCachedGuildMember());
    }

    @Test
    void invalidateTokenClearsCachedMembership() {
        AvoAuthService.initialize(new ModConfig());
        AvoAuthService service = AvoAuthService.getInstance();

        service.setCachedGuildMember(true);
        service.invalidateToken();

        assertNull(service.getCachedGuildMember());
        assertFalse(service.isGuildMember());
    }

    @Test
    void getSessionTokenReturnsCachedTokenWithoutNetwork() {
        AvoAuthService.initialize(new ModConfig());
        AvoAuthService service = AvoAuthService.getInstance();

        TestReflection.set(service, "sessionToken", "test-token");
        TestReflection.set(service, "sessionTokenExpiry", System.currentTimeMillis() + 60_000L);

        CompletableFuture<String> future = service.getSessionToken();

        assertEquals("test-token", future.join());
    }

    @Test
    void initializeIsIdempotent() {
        AvoAuthService.initialize(new ModConfig());
        AvoAuthService.initialize(new ModConfig());

        assertNotNull(AvoAuthService.getInstance());
    }

    @Test
    void extractErrorMessageReadsErrorField() {
        assertEquals("Bad token", invokeExtractErrorMessage("{\"error\":\"Bad token\"}"));
    }

    @Test
    void extractErrorMessageReturnsNullWhenMissing() {
        assertNull(invokeExtractErrorMessage("{\"message\":\"x\"}"));
        assertNull(invokeExtractErrorMessage("not json"));
    }

    private static String invokeExtractErrorMessage(String body) {
        try {
            Method method = AvoAuthService.class.getDeclaredMethod("extractErrorMessage", String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, body);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke extractErrorMessage", e);
        }
    }
}
