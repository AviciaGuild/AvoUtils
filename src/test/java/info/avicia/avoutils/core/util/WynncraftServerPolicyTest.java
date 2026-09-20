package info.avicia.avoutils.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WynncraftServerPolicyTest {

    @AfterEach
    void tearDown() {
        WynncraftServerPolicy.setScopeOverride(null);
    }

    @Test
    void classifyAddressAllowsMainWynncraftHosts() {
        assertEquals(WynncraftServerPolicy.Scope.MAIN, WynncraftServerPolicy.classifyAddress("wynncraft.com"));
        assertEquals(WynncraftServerPolicy.Scope.MAIN, WynncraftServerPolicy.classifyAddress("wynncraft.net"));
        assertEquals(WynncraftServerPolicy.Scope.MAIN, WynncraftServerPolicy.classifyAddress("wynncraft.org"));
        assertEquals(WynncraftServerPolicy.Scope.MAIN, WynncraftServerPolicy.classifyAddress("play.wynncraft.com"));
        assertEquals(WynncraftServerPolicy.Scope.MAIN, WynncraftServerPolicy.classifyAddress("play.wynncraft.net"));
        assertEquals(WynncraftServerPolicy.Scope.MAIN, WynncraftServerPolicy.classifyAddress("play.wynncraft.org"));
        assertEquals(WynncraftServerPolicy.Scope.MAIN, WynncraftServerPolicy.classifyAddress("wc3.wynncraft.com:25565"));
        assertEquals(WynncraftServerPolicy.Scope.MAIN, WynncraftServerPolicy.classifyAddress("lobby.wynncraft.com"));
        assertEquals(WynncraftServerPolicy.Scope.MAIN, WynncraftServerPolicy.classifyAddress("PLAY.WYNNCRAFT.COM"));
    }

    @Test
    void classifyAddressIdentifiesBetaHosts() {
        assertEquals(WynncraftServerPolicy.Scope.BETA, WynncraftServerPolicy.classifyAddress("beta.wynncraft.com"));
        assertEquals(WynncraftServerPolicy.Scope.BETA, WynncraftServerPolicy.classifyAddress("beta.wynncraft.net"));
        assertEquals(WynncraftServerPolicy.Scope.BETA, WynncraftServerPolicy.classifyAddress("beta.wynncraft.org"));
        assertEquals(WynncraftServerPolicy.Scope.BETA, WynncraftServerPolicy.classifyAddress("wc1.beta.wynncraft.com:25565"));
        assertEquals(WynncraftServerPolicy.Scope.BETA, WynncraftServerPolicy.classifyAddress("wc1.beta.wynncraft.net:25565"));
        assertEquals(WynncraftServerPolicy.Scope.BETA, WynncraftServerPolicy.classifyAddress("BETA.WYNNCRAFT.COM"));
    }

    @Test
    void classifyAddressBlocksUnknownAndNonWynncraftHosts() {
        assertEquals(WynncraftServerPolicy.Scope.BLOCKED, WynncraftServerPolicy.classifyAddress("localhost:25565"));
        assertEquals(WynncraftServerPolicy.Scope.BLOCKED, WynncraftServerPolicy.classifyAddress("127.0.0.1"));
        assertEquals(WynncraftServerPolicy.Scope.BLOCKED, WynncraftServerPolicy.classifyAddress("mc.hypixel.net"));
        assertEquals(WynncraftServerPolicy.Scope.BLOCKED, WynncraftServerPolicy.classifyAddress("example.com"));
        assertEquals(WynncraftServerPolicy.Scope.BLOCKED, WynncraftServerPolicy.classifyAddress("notwynncraft.com"));
        assertEquals(WynncraftServerPolicy.Scope.BLOCKED, WynncraftServerPolicy.classifyAddress("fakewynncraft.com"));
        assertEquals(WynncraftServerPolicy.Scope.UNKNOWN, WynncraftServerPolicy.classifyAddress(null));
        assertEquals(WynncraftServerPolicy.Scope.UNKNOWN, WynncraftServerPolicy.classifyAddress(""));
        assertEquals(WynncraftServerPolicy.Scope.UNKNOWN, WynncraftServerPolicy.classifyAddress("   "));
    }

    @Test
    void classifyCurrentServerTreatsSingleplayerAsBlocked() {
        assertEquals(
                WynncraftServerPolicy.Scope.BLOCKED,
                WynncraftServerPolicy.classifyCurrentServer(null, false, true, false));
        assertEquals(
                WynncraftServerPolicy.Scope.BLOCKED,
                WynncraftServerPolicy.classifyCurrentServer("play.wynncraft.com", true, false, true));
        assertEquals(
                WynncraftServerPolicy.Scope.BLOCKED,
                WynncraftServerPolicy.classifyCurrentServer("play.wynncraft.com", false, true, true));
    }

    @Test
    void classifyCurrentServerTreatsMultiplayerTransferAsUnknown() {
        assertEquals(
                WynncraftServerPolicy.Scope.UNKNOWN,
                WynncraftServerPolicy.classifyCurrentServer(null, false, false, true));
    }

    @Test
    void classifyCurrentServerTreatsMenuWithoutServerAsBlocked() {
        assertEquals(
                WynncraftServerPolicy.Scope.BLOCKED,
                WynncraftServerPolicy.classifyCurrentServer(null, false, false, false));
    }

    @Test
    void normalizeHostStripsSchemePortAndTrailingDot() {
        assertEquals("play.wynncraft.com", WynncraftServerPolicy.normalizeHost("https://play.wynncraft.com:443/."));
        assertEquals("beta.wynncraft.com", WynncraftServerPolicy.normalizeHost("beta.wynncraft.com."));
        assertEquals("play.wynncraft.net", WynncraftServerPolicy.normalizeHost("http://play.wynncraft.net:25565"));
        assertNull(WynncraftServerPolicy.normalizeHost("   "));
        assertNull(WynncraftServerPolicy.normalizeHost(null));
        assertNull(WynncraftServerPolicy.normalizeHost("[2001:db8::1]"));
    }

    @Test
    void testScopeOverrideAndPermissions() {
        WynncraftServerPolicy.setScopeOverride(() -> WynncraftServerPolicy.Scope.MAIN);
        assertTrue(WynncraftServerPolicy.isOnWynncraft());
        assertTrue(WynncraftServerPolicy.isNetworkingAllowed());

        WynncraftServerPolicy.setScopeOverride(() -> WynncraftServerPolicy.Scope.BETA);
        assertTrue(WynncraftServerPolicy.isOnWynncraft());
        assertFalse(WynncraftServerPolicy.isNetworkingAllowed());

        WynncraftServerPolicy.setScopeOverride(() -> WynncraftServerPolicy.Scope.BLOCKED);
        assertFalse(WynncraftServerPolicy.isOnWynncraft());
        assertFalse(WynncraftServerPolicy.isNetworkingAllowed());

        WynncraftServerPolicy.setScopeOverride(() -> WynncraftServerPolicy.Scope.UNKNOWN);
        assertFalse(WynncraftServerPolicy.isOnWynncraft());
        assertFalse(WynncraftServerPolicy.isNetworkingAllowed());
    }
}
