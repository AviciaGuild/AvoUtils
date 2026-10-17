package info.avicia.avoutils.core.config;

import com.google.gson.Gson;
import info.avicia.avoutils.BuildConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ModConfigTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    @Test
    void loadCreatesDefaultsWhenFileMissing() throws Exception {
        Path configPath = tempDir.resolve("avoutils.json");
        ModConfig config = ModConfig.load(configPath);

        assertTrue(Files.exists(configPath));
        assertEquals(BuildConfig.API_BASE_URL, config.getApiBaseUrl());
        // The built-in address isn't written out, so moving the backend never needs a config edit
        assertFalse(Files.readString(configPath).contains("apiBaseUrl"));
    }

    @Test
    void loadReadsExistingConfig() throws Exception {
        Path configPath = tempDir.resolve("avoutils.json");
        Files.writeString(configPath,
                "{\"apiBaseUrl\":\"http://localhost:8080\",\"chatBridgeEnabled\":false,"
                        + "\"guildStorageEmeraldThresholdPercent\":42}");

        ModConfig config = ModConfig.load(configPath);

        assertEquals("http://localhost:8080", config.getApiBaseUrl());
        assertFalse(config.chatBridgeEnabled);
        assertEquals(42, config.guildStorageEmeraldThresholdPercent);
    }

    @Test
    void loadIgnoresRemoteBackendOverride() throws Exception {
        Path configPath = tempDir.resolve("avoutils.json");
        Files.writeString(configPath, "{\"apiBaseUrl\":\"https://example.com\",\"chatBridgeEnabled\":false}");

        ModConfig config = ModConfig.load(configPath);

        assertNull(config.apiBaseUrl);
        assertEquals(BuildConfig.API_BASE_URL, config.getApiBaseUrl());
        assertFalse(config.chatBridgeEnabled);
    }

    @Test
    void loadResetsOutOfRangeThresholds() throws Exception {
        Path configPath = tempDir.resolve("avoutils.json");
        Files.writeString(configPath,
                "{\"guildStorageEmeraldThresholdPercent\":0,\"guildStorageAspectThresholdPercent\":150}");

        ModConfig config = ModConfig.load(configPath);

        assertEquals(90, config.guildStorageEmeraldThresholdPercent);
        assertEquals(90, config.guildStorageAspectThresholdPercent);
    }

    @Test
    void loadFallsBackToDefaultsOnInvalidJson() throws Exception {
        Path configPath = tempDir.resolve("avoutils.json");
        Files.writeString(configPath, "not json");

        ModConfig config = ModConfig.load(configPath);

        assertEquals(BuildConfig.API_BASE_URL, config.getApiBaseUrl());
    }

    @Test
    void saveWritesJsonThatRoundTrips() throws Exception {
        Path configPath = tempDir.resolve("avoutils.json");
        ModConfig config = new ModConfig();
        config.apiBaseUrl = "http://localhost:8080";
        config.chatBridgeEnabled = false;

        config.save(configPath);

        String json = Files.readString(configPath);
        assertTrue(json.contains("http://localhost:8080"));
    }

    @Test
    void defaultsAreSane() {
        ModConfig config = new ModConfig();
        assertNull(config.apiBaseUrl);
        assertEquals(BuildConfig.API_BASE_URL, config.getApiBaseUrl());
        assertTrue(config.chatBridgeEnabled);
        assertTrue(config.emojiEnabled);
        assertTrue(config.emojiAutocompleteEnabled);
        assertTrue(config.guildStorageNotifsEnabled);
        assertEquals(90, config.guildStorageEmeraldThresholdPercent);
        assertEquals(90, config.guildStorageAspectThresholdPercent);
        assertTrue(config.updateRemindersEnabled);
    }

    @Test
    void gsonRoundTripPreservesFields() {
        ModConfig original = new ModConfig();
        original.apiBaseUrl = "http://localhost:8080";
        original.chatBridgeEnabled = false;
        original.guildStorageEmeraldThresholdPercent = 42;

        String json = GSON.toJson(original);
        ModConfig restored = GSON.fromJson(json, ModConfig.class);

        assertEquals("http://localhost:8080", restored.apiBaseUrl);
        assertFalse(restored.chatBridgeEnabled);
        assertEquals(42, restored.guildStorageEmeraldThresholdPercent);
    }

    @Test
    void validateIgnoresRemoteOverrides() {
        ModConfig config = new ModConfig();
        for (String remote : new String[]{"http://evil.example.com", "https://example.com"}) {
            config.apiBaseUrl = remote;
            invokeValidate(config);
            assertNull(config.apiBaseUrl, remote);
            assertEquals(BuildConfig.API_BASE_URL, config.getApiBaseUrl(), remote);
        }
    }

    @Test
    void validateRejectsLookalikeLocalHosts() {
        ModConfig config = new ModConfig();
        for (String lookalike : new String[]{
                "http://localhost.example.com", "http://127.0.0.1.example.com", "http://localhost@example.com"}) {
            config.apiBaseUrl = lookalike;
            invokeValidate(config);
            assertNull(config.apiBaseUrl, lookalike);
        }
    }

    @Test
    void validateAllowsLocalhostOverrides() {
        ModConfig config = new ModConfig();
        for (String local : new String[]{"http://localhost:8080", "http://127.0.0.1:8080", "https://localhost"}) {
            config.apiBaseUrl = local;
            invokeValidate(config);
            assertEquals(local, config.apiBaseUrl);
            assertEquals(local, config.getApiBaseUrl());
        }
    }

    @Test
    void validateStripsTrailingSlash() {
        ModConfig config = new ModConfig();
        config.apiBaseUrl = "http://localhost:8080/";
        invokeValidate(config);
        assertEquals("http://localhost:8080", config.apiBaseUrl);
    }

    @Test
    void validateDropsBuiltInAddressWrittenByOlderVersions() {
        // Only meaningful when the build uses a remote backend (the normal case)
        assumeFalse(ModConfig.isLocalBackend(BuildConfig.API_BASE_URL));
        ModConfig config = new ModConfig();
        config.apiBaseUrl = BuildConfig.API_BASE_URL + "/";
        invokeValidate(config);
        assertNull(config.apiBaseUrl);
        assertEquals(BuildConfig.API_BASE_URL, config.getApiBaseUrl());
    }

    @Test
    void validateKeepsMissingOverrideEmpty() {
        ModConfig config = new ModConfig();
        config.apiBaseUrl = null;
        invokeValidate(config);
        assertNull(config.apiBaseUrl);
        assertEquals(BuildConfig.API_BASE_URL, config.getApiBaseUrl());
    }

    @Test
    void getApiBaseUrlIgnoresRemoteValueSetDirectly() {
        ModConfig config = new ModConfig();
        config.apiBaseUrl = "https://example.com"; // bypasses validate()
        assertEquals(BuildConfig.API_BASE_URL, config.getApiBaseUrl());
    }

    private static void invokeValidate(ModConfig config) {
        try {
            Method method = ModConfig.class.getDeclaredMethod("validate");
            method.setAccessible(true);
            method.invoke(config);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke ModConfig.validate", e);
        }
    }
}
