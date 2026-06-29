package info.avicia.avoutils.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import info.avicia.avoutils.AvoUtilsMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod configuration
 * Stores the API base URL (pointing to the AvoBot server)
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "avoutils.json";

    private static final String DEFAULT_API_BASE_URL = "https://auth.avicia.info:8443";
    public String apiBaseUrl = DEFAULT_API_BASE_URL;

    /**
     * Load config from disk, or create a default one if it doesn't exist
     */
    public static ModConfig load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                ModConfig config = GSON.fromJson(json, ModConfig.class);
                if (config != null) {
                    config.validate();
                    return config;
                }
            } catch (Exception e) {
                AvoUtilsMod.LOGGER.error("Failed to load config, using defaults.", e);
            }
        }
        // Create default config
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }

    /**
     * Validates mutable config fields, resetting any unsafe values to their defaults
     */
    private void validate() {
        if (apiBaseUrl == null || (!apiBaseUrl.startsWith("https://") && !apiBaseUrl.startsWith("http://localhost") && !apiBaseUrl.startsWith("http://127.0.0.1"))) {
            AvoUtilsMod.LOGGER.warn(
                "Config 'apiBaseUrl' is missing or does not use HTTPS (got '{}')."
                    + " Resetting to default: {}", apiBaseUrl, DEFAULT_API_BASE_URL);
            apiBaseUrl = DEFAULT_API_BASE_URL;
        }
        if (apiBaseUrl.endsWith("/")) {
            apiBaseUrl = apiBaseUrl.substring(0, apiBaseUrl.length() - 1);
        }
    }

    /**
     * Save config to disk
     */
    public void save() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(this));
        } catch (IOException e) {
            AvoUtilsMod.LOGGER.error("Failed to save config.", e);
        }
    }
}
