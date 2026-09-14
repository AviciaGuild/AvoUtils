package info.avicia.avoutils.features.updater;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckResultTest {

    @Test
    void upToDateFactorySetsCorrectFields() {
        UpdateCheckResult result = UpdateCheckResult.upToDate("1.0.0");

        assertFalse(result.updateAvailable());
        assertEquals("1.0.0", result.currentVersion());
        assertEquals("1.0.0", result.latestVersion());
        assertNull(result.downloadUrl());
        assertNull(result.sha512Hash());
        assertNull(result.fileName());
        assertEquals(0, result.fileSize());
        assertNull(result.changelog());
    }

    @Test
    void recordConstructorRetainsAllFields() {
        UpdateCheckResult result = new UpdateCheckResult(
                true, "1.0.0", "1.1.0",
                "https://example.com/mod.jar", "sha123",
                "avoutils-1.1.0.jar", 42000, "Fixes"
        );

        assertTrue(result.updateAvailable());
        assertEquals("1.0.0", result.currentVersion());
        assertEquals("1.1.0", result.latestVersion());
        assertEquals("https://example.com/mod.jar", result.downloadUrl());
        assertEquals("sha123", result.sha512Hash());
        assertEquals("avoutils-1.1.0.jar", result.fileName());
        assertEquals(42000, result.fileSize());
        assertEquals("Fixes", result.changelog());
    }
}

