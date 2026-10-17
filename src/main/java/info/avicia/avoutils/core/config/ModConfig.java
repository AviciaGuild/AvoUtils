package info.avicia.avoutils.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.BuildConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

/**
 * Mod configuration
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "avoutils.json";
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    public String apiBaseUrl = null;
    public boolean chatBridgeEnabled = true;
    public boolean emojiEnabled = true;
    public boolean emojiAutocompleteEnabled = true;
    public boolean newPartyNotifsEnabled = true;
    public boolean notificationSoundsEnabled = true;
    public boolean guildStorageNotifsEnabled = true;
    public boolean guildStorageNotifSoundsEnabled = true;
    public int guildStorageEmeraldThresholdPercent = 90;
    public int guildStorageAspectThresholdPercent = 90;
    public boolean updateRemindersEnabled = true;

    /**
     * Load config from disk, or create a default one if it doesn't exist
     */
    public static ModConfig load() {
        return load(FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE));
    }

    static ModConfig load(Path configPath) {
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                ModConfig config = GSON.fromJson(json, ModConfig.class);
                if (config != null) {
                    config.validate();
                    return config;
                }
            } catch (Exception e) {
                AvoUtilsMod.LOGGER.error("[AvoUtils] Failed to load config, using defaults.", e);
            }
        }
        // Create default config
        ModConfig config = new ModConfig();
        config.save(configPath);
        return config;
    }

    /**
     * Validates mutable config fields, resetting any unsafe values to their defaults
     */
    private void validate() {
        apiBaseUrl = sanitizeBackendOverride(apiBaseUrl);
        for (Setting setting : Settings.ALL) {
            setting.validate(this);
        }
    }

    /**
     * Backend address to use: a localhost override from the config file, otherwise the address built
     * into the jar. Checked on every read.
     */
    public String getApiBaseUrl() {
        return isLocalBackend(apiBaseUrl) ? stripTrailingSlashes(apiBaseUrl) : BuildConfig.API_BASE_URL;
    }

    /**
     * Keeps a configured override only if it points at this machine. Anything else is dropped.
     */
    static String sanitizeBackendOverride(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String normalized = stripTrailingSlashes(url.trim());
        if (isLocalBackend(normalized)) {
            return normalized;
        }
        // Older versions wrote the built-in address into the file; drop that silently
        if (!normalized.equals(BuildConfig.API_BASE_URL)) {
            AvoUtilsMod.LOGGER.warn(
                "[AvoUtils] Ignoring config 'apiBaseUrl' ({}): only localhost addresses can override the"
                    + " built-in backend ({}).", normalized, BuildConfig.API_BASE_URL);
        }
        return null;
    }

    /**
     * Whether {@code url} is an http(s) address whose host is exactly this machine.
     */
    static boolean isLocalBackend(String url) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && host != null
                && LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String stripTrailingSlashes(String url) {
        String trimmed = url.trim();
        int end = trimmed.length();
        while (end > 0 && trimmed.charAt(end - 1) == '/') {
            end--;
        }
        return trimmed.substring(0, end);
    }

    /**
     * Save config to disk
     */
    public void save() {
        try {
            if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getConfigDir() != null) {
                save(FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE));
            }
        } catch (Throwable e) {
            AvoUtilsMod.LOGGER.warn("[AvoUtils] Could not save config to Fabric config directory: {}", e.getMessage());
        }
    }

    void save(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            Path tempPath = configPath.resolveSibling(configPath.getFileName().toString() + ".tmp");
            Files.writeString(tempPath, GSON.toJson(this));
            Files.move(tempPath, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] Failed to save config.", e);
        }
    }
}
