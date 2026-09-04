package info.avicia.avoutils.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientVersionTest {

    @Test
    void headerConstantMatchesContract() {
        assertEquals("X-AvoUtils-Version", ClientVersion.MOD_VERSION_HEADER);
        assertTrue(ClientVersion.MOD_VERSION_HEADER.startsWith("X-"));
    }

    @Test
    void resolveInstalledVersionDoesNotThrow() {
        String version = ClientVersion.resolveInstalledVersion();
        assertNotNull(version);
    }
}

