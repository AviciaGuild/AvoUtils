package info.avicia.avoutils.core.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModConfigTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    @Test
    void loadCreatesDefaultsWhenFileMissing() {
        Path configPath = tempDir.resolve("avoutils.json");
        ModConfig config = ModConfig.load(configPath);

        assertTrue(Files.exists(configPath));
        assertEquals("https://auth.avicia.info:8443", config.apiBaseUrl);
    }

    @Test
    void loadReadsExistingConfig() throws Exception {
        Path configPath = tempDir.resolve("avoutils.json");
        Files.writeString(configPath,
                "{\"apiBaseUrl\":\"https://example.com\",\"chatBridgeEnabled\":false,"
                        + "\"guildStorageEmeraldThresholdPercent\":42}");

        ModConfig config = ModConfig.load(configPath);

        assertEquals("https://example.com", config.apiBaseUrl);
        assertFalse(config.chatBridgeEnabled);
        assertEquals(42, config.guildStorageEmeraldThresholdPercent);
    }

    @Test
    void loadFallsBackToDefaultsOnInvalidJson() throws Exception {
        Path configPath = tempDir.resolve("avoutils.json");
        Files.writeString(configPath, "not json");

        ModConfig config = ModConfig.load(configPath);

        assertEquals("https://auth.avicia.info:8443", config.apiBaseUrl);
    }

    @Test
    void saveWritesJsonThatRoundTrips() throws Exception {
        Path configPath = tempDir.resolve("avoutils.json");
        ModConfig config = new ModConfig();
        config.apiBaseUrl = "https://example.com";
        config.chatBridgeEnabled = false;

        config.save(configPath);

        String json = Files.readString(configPath);
        assertTrue(json.contains("https://example.com"));
    }

    @Test
    void defaultsAreSane() {
        ModConfig config = new ModConfig();
        assertEquals("https://auth.avicia.info:8443", config.apiBaseUrl);
        assertTrue(config.chatBridgeEnabled);
        assertTrue(config.emojiEnabled);
        assertTrue(config.emojiAutocompleteEnabled);
        assertTrue(config.guildStorageNotifsEnabled);
        assertEquals(90, config.guildStorageEmeraldThresholdPercent);
        assertEquals(90, config.guildStorageAspectThresholdPercent);
    }

    @Test
    void gsonRoundTripPreservesFields() {
        ModConfig original = new ModConfig();
        original.apiBaseUrl = "https://example.com";
        original.chatBridgeEnabled = false;
        original.guildStorageEmeraldThresholdPercent = 42;

        String json = GSON.toJson(original);
        ModConfig restored = GSON.fromJson(json, ModConfig.class);

        assertEquals("https://example.com", restored.apiBaseUrl);
        assertFalse(restored.chatBridgeEnabled);
        assertEquals(42, restored.guildStorageEmeraldThresholdPercent);
    }

    @Test
    void validateRejectsNonHttpsBaseUrl() {
        ModConfig config = new ModConfig();
        config.apiBaseUrl = "http://evil.example.com";
        invokeValidate(config);
        assertEquals("https://auth.avicia.info:8443", config.apiBaseUrl);
    }

    @Test
    void validateAllowsLocalhostHttp() {
        ModConfig config = new ModConfig();
        config.apiBaseUrl = "http://localhost:8080";
        invokeValidate(config);
        assertEquals("http://localhost:8080", config.apiBaseUrl);

        config.apiBaseUrl = "http://127.0.0.1:8080";
        invokeValidate(config);
        assertEquals("http://127.0.0.1:8080", config.apiBaseUrl);
    }

    @Test
    void validateStripsTrailingSlash() {
        ModConfig config = new ModConfig();
        config.apiBaseUrl = "https://auth.avicia.info:8443/";
        invokeValidate(config);
        assertEquals("https://auth.avicia.info:8443", config.apiBaseUrl);
    }

    @Test
    void validateResetsNullBaseUrl() {
        ModConfig config = new ModConfig();
        config.apiBaseUrl = null;
        invokeValidate(config);
        assertEquals("https://auth.avicia.info:8443", config.apiBaseUrl);
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
