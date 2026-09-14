package info.avicia.avoutils.features.updater;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ModrinthUpdateCheckerTest {

    private ModrinthUpdateChecker checker;

    @BeforeEach
    void setUp() {
        HttpClient mockHttpClient = mock(HttpClient.class);
        checker = new ModrinthUpdateChecker(mockHttpClient);
    }

    @Test
    void isNewerCorrectlyComparesSemanticVersions() {
        assertTrue(ModrinthUpdateChecker.isNewer("1.0.1", "1.0.0"));
        assertTrue(ModrinthUpdateChecker.isNewer("1.1.0", "1.0.9"));
        assertTrue(ModrinthUpdateChecker.isNewer("2.0.0", "1.9.9"));
        assertTrue(ModrinthUpdateChecker.isNewer("1.0.0-beta.2", "1.0.0-beta.1"));
        assertTrue(ModrinthUpdateChecker.isNewer("1.0.0", "1.0.0-beta.1"));

        assertFalse(ModrinthUpdateChecker.isNewer("1.0.0", "1.0.0"));
        assertFalse(ModrinthUpdateChecker.isNewer("1.0.0", "1.0.1"));
        assertFalse(ModrinthUpdateChecker.isNewer("0.9.9", "1.0.0"));
    }

    @Test
    void isNewerHandlesInvalidVersionsGracefully() {
        assertFalse(ModrinthUpdateChecker.isNewer("invalid-version", "1.0.0"));
        assertFalse(ModrinthUpdateChecker.isNewer("1.0.0", "not-a-version"));
    }

    @Test
    void parseVersionResponseDetectsNewerRelease() {
        String json = """
            [
              {
                "id": "abc123",
                "version_number": "1.2.0",
                "version_type": "release",
                "changelog": "Added cool features",
                "files": [
                  {
                    "url": "https://cdn.modrinth.com/data/avoutils/versions/abc123/avoutils-1.2.0.jar",
                    "filename": "avoutils-1.2.0.jar",
                    "primary": true,
                    "size": 102400,
                    "hashes": {
                      "sha512": "deadbeef512"
                    }
                  }
                ]
              }
            ]
            """;

        UpdateCheckResult result = checker.parseVersionResponse(json, "1.1.0");

        assertTrue(result.updateAvailable());
        assertEquals("1.1.0", result.currentVersion());
        assertEquals("1.2.0", result.latestVersion());
        assertEquals("https://cdn.modrinth.com/data/avoutils/versions/abc123/avoutils-1.2.0.jar", result.downloadUrl());
        assertEquals("avoutils-1.2.0.jar", result.fileName());
        assertEquals(102400, result.fileSize());
        assertEquals("deadbeef512", result.sha512Hash());
        assertEquals("Added cool features", result.changelog());
    }

    @Test
    void parseVersionResponseIgnoresBetaVersions() {
        String json = """
            [
              {
                "id": "beta-1",
                "version_number": "1.3.0-beta.1",
                "version_type": "beta",
                "files": [
                  {
                    "url": "https://cdn.modrinth.com/beta.jar",
                    "filename": "avoutils-1.3.0-beta.1.jar",
                    "primary": true,
                    "size": 50000,
                    "hashes": { "sha512": "beta512" }
                  }
                ]
              },
              {
                "id": "rel-1",
                "version_number": "1.2.0",
                "version_type": "release",
                "files": [
                  {
                    "url": "https://cdn.modrinth.com/release.jar",
                    "filename": "avoutils-1.2.0.jar",
                    "primary": true,
                    "size": 60000,
                    "hashes": { "sha512": "release512" }
                  }
                ]
              }
            ]
            """;

        // Current version 1.1.0: should pick 1.2.0 (release), NOT 1.3.0-beta.1
        UpdateCheckResult result = checker.parseVersionResponse(json, "1.1.0");

        assertTrue(result.updateAvailable());
        assertEquals("1.2.0", result.latestVersion());
        assertEquals("https://cdn.modrinth.com/release.jar", result.downloadUrl());
        assertEquals("release512", result.sha512Hash());
    }

    @Test
    void parseVersionResponseReturnsUpToDateWhenCurrentIsEqualOrNewer() {
        String json = """
            [
              {
                "id": "rel-1",
                "version_number": "1.2.0",
                "version_type": "release",
                "files": [
                  {
                    "url": "https://cdn.modrinth.com/release.jar",
                    "filename": "avoutils-1.2.0.jar",
                    "primary": true,
                    "size": 60000,
                    "hashes": { "sha512": "release512" }
                  }
                ]
              }
            ]
            """;

        UpdateCheckResult resultSame = checker.parseVersionResponse(json, "1.2.0");
        assertFalse(resultSame.updateAvailable());
        assertEquals("1.2.0", resultSame.currentVersion());

        UpdateCheckResult resultNewer = checker.parseVersionResponse(json, "1.3.0");
        assertFalse(resultNewer.updateAvailable());
        assertEquals("1.3.0", resultNewer.currentVersion());
    }

    @Test
    void parseVersionResponsePicksPrimaryFileOverFirstFile() {
        String json = """
            [
              {
                "id": "rel-1",
                "version_number": "1.2.0",
                "version_type": "release",
                "files": [
                  {
                    "url": "https://cdn.modrinth.com/sources.jar",
                    "filename": "avoutils-1.2.0-sources.jar",
                    "primary": false,
                    "size": 10000,
                    "hashes": { "sha512": "sources512" }
                  },
                  {
                    "url": "https://cdn.modrinth.com/mod.jar",
                    "filename": "avoutils-1.2.0.jar",
                    "primary": true,
                    "size": 60000,
                    "hashes": { "sha512": "mod512" }
                  }
                ]
              }
            ]
            """;

        UpdateCheckResult result = checker.parseVersionResponse(json, "1.0.0");

        assertTrue(result.updateAvailable());
        assertEquals("avoutils-1.2.0.jar", result.fileName());
        assertEquals("https://cdn.modrinth.com/mod.jar", result.downloadUrl());
        assertEquals("mod512", result.sha512Hash());
    }

    @Test
    void parseVersionResponseHandlesEmptyVersionList() {
        UpdateCheckResult result = checker.parseVersionResponse("[]", "1.0.0");
        assertFalse(result.updateAvailable());
        assertEquals("1.0.0", result.currentVersion());
    }

    @Test
    void parseVersionResponseHandlesMissingHashesGracefully() {
        String json = """
            [
              {
                "id": "rel-1",
                "version_number": "1.2.0",
                "version_type": "release",
                "files": [
                  {
                    "url": "https://cdn.modrinth.com/mod.jar",
                    "filename": "avoutils-1.2.0.jar",
                    "primary": true,
                    "size": 60000
                  }
                ]
              }
            ]
            """;

        UpdateCheckResult result = checker.parseVersionResponse(json, "1.0.0");
        assertTrue(result.updateAvailable());
        assertNull(result.sha512Hash());
    }

    @Test
    void extractVersionFromFileNameMatchesValidFormats() {
        assertEquals("1.4.2", ModrinthUpdateChecker.extractVersionFromFileName("avoutils-1.4.2.jar"));
        assertEquals("2.0.0", ModrinthUpdateChecker.extractVersionFromFileName("AvoUtils-2.0.0.jar"));
        assertEquals("1.0.0-beta.1", ModrinthUpdateChecker.extractVersionFromFileName("avoutils_1.0.0-beta.1.jar"));
        assertNull(ModrinthUpdateChecker.extractVersionFromFileName("othermod-1.0.0.jar"));
        assertNull(ModrinthUpdateChecker.extractVersionFromFileName("avoutils.jar"));
    }

    @Test
    void findPendingUpdateDetectsNewerPendingJar(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Path stagingDir = modsDir.resolve(ModrinthUpdateChecker.STAGING_DIR_NAME);
        Files.createDirectories(stagingDir);

        Path pendingFile = stagingDir.resolve("avoutils-1.2.0.jar.pending");
        Files.writeString(pendingFile, "dummy jar content");

        Optional<ModrinthUpdateChecker.PendingUpdate> update =
                ModrinthUpdateChecker.findPendingUpdate(modsDir, "1.1.0");

        assertTrue(update.isPresent());
        assertEquals("1.2.0", update.get().version());
        assertEquals(pendingFile, update.get().pendingJar());
        assertEquals(modsDir.resolve("avoutils-1.2.0.jar"), update.get().targetJar());
    }

    @Test
    void findPendingUpdateIgnoresOlderOrEqualPendingJar(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Path stagingDir = modsDir.resolve(ModrinthUpdateChecker.STAGING_DIR_NAME);
        Files.createDirectories(stagingDir);

        Files.writeString(stagingDir.resolve("avoutils-1.0.0.jar.pending"), "dummy");

        Optional<ModrinthUpdateChecker.PendingUpdate> update =
                ModrinthUpdateChecker.findPendingUpdate(modsDir, "1.0.0");

        assertFalse(update.isPresent());
    }

    @Test
    void cleanupStagingDirRetainsSpecifiedPendingFile(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Path stagingDir = modsDir.resolve(ModrinthUpdateChecker.STAGING_DIR_NAME);
        Files.createDirectories(stagingDir);

        Path keepPending = stagingDir.resolve("avoutils-1.2.0.jar.pending");
        Path stalePending = stagingDir.resolve("avoutils-1.1.0.jar.pending");
        Path tempFile = stagingDir.resolve("download.tmp");

        Files.writeString(keepPending, "keep");
        Files.writeString(stalePending, "stale");
        Files.writeString(tempFile, "temp");

        ModrinthUpdateChecker.cleanupStagingDir(modsDir, keepPending);

        assertTrue(Files.exists(keepPending));
        assertFalse(Files.exists(stalePending));
        assertFalse(Files.exists(tempFile));
    }
}
