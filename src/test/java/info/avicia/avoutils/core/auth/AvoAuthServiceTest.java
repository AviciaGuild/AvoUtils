package info.avicia.avoutils.core.auth;

import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.testutil.TestReflection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvoAuthServiceTest {

    private AvoAuthService service;

    @BeforeEach
    void setUp() {
        AvoAuthService.initialize(new ModConfig());
        this.service = AvoAuthService.getInstance();
    }

    @Test
    void cachedGuildMemberTracksMembership() {
        assertFalse(service.isGuildMember());
        service.setCachedGuildMember(true);

        assertTrue(service.isGuildMember());
        assertTrue(service.getCachedGuildMember());
    }

    @Test
    void invalidateTokenClearsCachedMembership() {
        service.setCachedGuildMember(true);
        service.invalidateToken();

        assertNull(service.getCachedGuildMember());
        assertFalse(service.isGuildMember());
    }

    @Test
    void getSessionTokenReturnsCachedTokenWithoutNetwork() {
        TestReflection.set(service, "sessionToken", "test-token");
        TestReflection.set(service, "sessionTokenExpiry", System.currentTimeMillis() + 60_000L);

        CompletableFuture<String> future = service.getSessionToken();

        assertEquals("test-token", future.join());
    }

    @Test
    void resolveGuildMembershipReturnsCachedValueImmediately() {
        service.setCachedGuildMember(true);
        assertTrue(service.resolveGuildMembership().join());

        service.setCachedGuildMember(false);
        assertFalse(service.resolveGuildMembership().join());
    }

    @Test
    void resolveGuildMembershipHandlesFailureGracefully() {
        service.invalidateToken();
        // mc session is null in tests, so fetchSessionTokenAsync fails; resolveGuildMembership catches and returns false
        assertFalse(service.resolveGuildMembership().join());
    }

    @Test
    void runIfGuildMemberRunsAuthorizedImmediatelyWhenCachedTrue() {
        service.setCachedGuildMember(true);

        boolean[] flags = new boolean[2];
        service.runIfGuildMember(() -> flags[0] = true, () -> flags[1] = true);

        assertTrue(flags[0]);
        assertFalse(flags[1]);
    }

    @Test
    void runIfGuildMemberRunsDeniedImmediatelyWhenCachedFalse() {
        service.setCachedGuildMember(false);

        boolean[] flags = new boolean[2];
        service.runIfGuildMember(() -> flags[0] = true, () -> flags[1] = true);

        assertFalse(flags[0]);
        assertTrue(flags[1]);
    }

    @Test
    void runIfGuildMemberResolvesAsyncWhenUncached() {
        service.invalidateToken();

        boolean[] flags = new boolean[2];
        // In unit test without MC session, resolveGuildMembership fails and returns false -> runs onDenied
        service.runIfGuildMember(() -> flags[0] = true, () -> flags[1] = true);

        // Allow async completion
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {}

        assertFalse(flags[0]);
        assertTrue(flags[1]);
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

    private String invokeExtractErrorMessage(String body) {
        return TestReflection.invokeStatic(AvoAuthService.class, "extractErrorMessage",
                new Class[]{String.class}, body);
    }
}
