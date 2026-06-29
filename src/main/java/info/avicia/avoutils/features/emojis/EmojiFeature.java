package info.avicia.avoutils.features.emojis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class EmojiFeature implements AvoFeature {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("[^a-zA-Z0-9_.-]");
    private static final String TWEMOJI_ZIP_URL = "https://github.com/AmberWat/PixelTwemojiMC-18/releases/download/v8.0/PixelTwemojiMC-18.zip";
    private static final String TWEMOJI_RAW_JSON_PATH = "assets/twemoji/font/emoji_raw.json";
    private static final String TWEMOJI_SHORTCODES_JSON_PATH = "assets/emoji_shortcodes/lang/en_us.json";
    private static final String TWEMOJI_PACK_NAME = "file/avoutils-twemoji.zip";
    private static final String CUSTOM_EMOJIS_PACK_NAME = "file/avoutils-emojis";

    private final Path twemojiPath = FabricLoader.getInstance().getGameDir().resolve("resourcepacks/avoutils-twemoji.zip");
    private final Path packDir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks/avoutils-emojis");
    private final int packFormat = resolvePackFormat();

    private final Map<String, String> standardEmojis = new HashMap<>();
    private final Map<Integer, String> standardCharToPua = new HashMap<>();
    private final Map<String, String> customEmojis = new HashMap<>();

    private final AtomicBoolean loading = new AtomicBoolean(false);
    private volatile EmojiTrie activeTrie = new EmojiTrie();
    private FontConfig standardFontConfig;

    public EmojiTrie getActiveTrie() {
        return activeTrie;
    }

    @Override
    public void initialize(ModConfig config) {
        // Initialize resource pack directories
        try {
            createResourcePackStructure();
        } catch (IOException e) {
            AvoUtilsMod.LOGGER.error("Failed to create emoji resource pack folders", e);
        }

        // Register startup listener to load emojis and enable the packs
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            boolean wasEnabled = client.getResourcePackManager().getEnabledIds().contains(TWEMOJI_PACK_NAME)
                    && client.getResourcePackManager().getEnabledIds().contains(CUSTOM_EMOJIS_PACK_NAME);
            CompletableFuture.runAsync(() -> {
                downloadTwemojiPack();
                loadStandardEmojiResources();
                loadAndCacheEmojis();
                client.execute(() -> {
                    enableResourcePacks();
                    if (!wasEnabled) {
                        client.reloadResources();
                    }
                });
            });
        });
    }

    private void loadStandardEmojiResources() {
        if (!Files.exists(twemojiPath)) {
            AvoUtilsMod.LOGGER.error("Twemoji resource pack not found, standard emojis mapping skipped!");
            return;
        }

        try (ZipFile zip = new ZipFile(twemojiPath.toFile())) {
            loadPuaMappings(zip);
            loadStandardEmojis(zip);
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Failed to load standard emoji resources from ZIP", e);
        }
    }

    private void loadPuaMappings(ZipFile zip) {
        standardCharToPua.clear();
        ZipEntry entry = zip.getEntry(TWEMOJI_RAW_JSON_PATH);
        if (entry == null) {
            return;
        }
        try (InputStream is = zip.getInputStream(entry);
                InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            standardFontConfig = GSON.fromJson(isr, FontConfig.class);
            if (standardFontConfig != null && standardFontConfig.providers != null) {
                for (FontProvider prov : standardFontConfig.providers) {
                    for (String row : prov.chars) {
                        int i = 0;
                        while (i < row.length()) {
                            int codePoint = row.codePointAt(i);
                            int charCount = Character.charCount(codePoint);
                            if (codePoint > 32) {
                                if (!standardCharToPua.containsKey(codePoint)) {
                                    standardCharToPua.put(codePoint, String.valueOf((char) (0xE200 + standardCharToPua.size())));
                                }
                            }
                            i += charCount;
                        }
                    }
                }
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Failed to load Twemoji PUA mappings", e);
        }
    }

    private void loadStandardEmojis(ZipFile zip) {
        ZipEntry entry = zip.getEntry(TWEMOJI_SHORTCODES_JSON_PATH);
        if (entry == null) {
            AvoUtilsMod.LOGGER.error("en_us.json not found in Twemoji pack!");
            return;
        }
        try (InputStream is = zip.getInputStream(entry);
                InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(isr).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entrySet : root.entrySet()) {
                String shortcode = entrySet.getKey();
                String value = entrySet.getValue().getAsString();
                if (!value.isEmpty()) {
                    int cp = value.codePointAt(0);
                    String puaValue = standardCharToPua.get(cp);
                    if (puaValue != null) {
                        standardEmojis.put(shortcode, puaValue);
                    }
                }
            }
            AvoUtilsMod.LOGGER.info("Successfully loaded " + standardEmojis.size() + " standard Twemoji shortcodes from ZIP.");
            rebuildActiveEmojis();
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Failed to parse Twemoji shortcodes from ZIP", e);
        }
    }

    private void rebuildActiveEmojis() {
        Map<String, String> merged = new HashMap<>(standardEmojis);
        synchronized (customEmojis) {
            merged.putAll(customEmojis);
        }

        EmojiTrie newTrie = new EmojiTrie();
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            newTrie.insert(entry.getKey(), entry.getValue());
        }
        this.activeTrie = newTrie;
    }

    private void loadAndCacheEmojis() {
        if (!loading.compareAndSet(false, true))
            return;

        try {
            AvoUtilsMod.LOGGER.info("Starting loading and caching emojis...");
            Map<String, String> allEmojis = new HashMap<>();

            try (InputStream is = EmojiFeature.class.getResourceAsStream("/assets/avoutils/custom_emojis.json")) {
                if (is != null) {
                    try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        Type type = new TypeToken<Map<String, String>>() {
                        }.getType();
                        Map<String, String> setEmojis = GSON.fromJson(isr, type);
                        if (setEmojis != null) {
                            allEmojis.putAll(setEmojis);
                        }
                    }
                } else {
                    AvoUtilsMod.LOGGER.error("custom_emojis.json not found in mod resources!");
                }
            } catch (Exception e) {
                AvoUtilsMod.LOGGER.error("Failed to load custom emojis from resources", e);
            }

            Map<String, String> downloadedEmojis = new HashMap<>();
            if (!allEmojis.isEmpty()) {
                // Download images
                Path texturesDir = packDir.resolve("assets/avoutils/textures/font");
                Files.createDirectories(texturesDir);

                for (Map.Entry<String, String> entry : allEmojis.entrySet()) {
                    String emojiName = entry.getKey();
                    String imageUrl = entry.getValue();
                    String safeName = getSafeName(emojiName);
                    Path imagePath = texturesDir.resolve(safeName + ".png");

                    try {
                        // Download emoji image if it doesn't exist
                        if (!Files.exists(imagePath)) {
                            downloadImage(imageUrl, imagePath);
                        }
                        downloadedEmojis.put(emojiName, imageUrl);
                    } catch (Exception e) {
                        AvoUtilsMod.LOGGER.error("Failed to download emoji image for " + emojiName + " from " + imageUrl, e);
                        if (e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            // Rebuild Unicode mapping
            List<String> sortedNames = new ArrayList<>(downloadedEmojis.keySet());
            Collections.sort(sortedNames);

            synchronized (customEmojis) {
                customEmojis.clear();
                char currentUnicode = '\uF000';
                for (String name : sortedNames) {
                    customEmojis.put(":" + name + ":", String.valueOf(currentUnicode));
                    currentUnicode++;
                }
            }
            rebuildActiveEmojis();

            if (downloadedEmojis.isEmpty()) {
                AvoUtilsMod.LOGGER.info("No custom emojis loaded, proceeding with standard emojis.");
            } else {
                AvoUtilsMod.LOGGER.info("Successfully loaded " + downloadedEmojis.size() + " custom emojis.");
            }

            // Write font default.json
            writeFontJson();

            // Enable pack and reload resources if client is in-game
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.execute(() -> {
                    enableResourcePacks();
                    if (client.world != null) {
                        client.reloadResources();
                    }
                });
            }

        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Error occurred while loading emojis", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            loading.set(false);
        }
    }

    private void createResourcePackStructure() throws IOException {
        Path mcmetaPath = packDir.resolve("pack.mcmeta");
        boolean needsUpdate = true;

        if (Files.exists(mcmetaPath)) {
            try {
                String raw = Files.readString(mcmetaPath, StandardCharsets.UTF_8);
                JsonObject rootObj = JsonParser.parseString(raw).getAsJsonObject();
                int existingFormat = rootObj.getAsJsonObject("pack").get("pack_format").getAsInt();
                if (existingFormat == packFormat) {
                    needsUpdate = false;
                }
            } catch (Exception ignored) {
            }
        }

        if (needsUpdate) {
            Files.createDirectories(packDir);
            PackMetadata pack = new PackMetadata(new PackInfo(packFormat, "Dynamic emojis for AvoUtils"));
            Files.writeString(mcmetaPath, GSON.toJson(pack));
        }

        if (!Files.exists(twemojiPath)) {
            Files.deleteIfExists(packDir.resolve("assets/minecraft/font/default.json"));
        }
    }

    private void writeFontJson() throws IOException {
        Path defaultFontPath = packDir.resolve("assets/minecraft/font/default.json");
        Files.createDirectories(defaultFontPath.getParent());

        FontConfig defaultFontConfig = new FontConfig();

        // Add custom emojis to default font
        synchronized (customEmojis) {
            for (Map.Entry<String, String> entry : customEmojis.entrySet()) {
                String fullTrigger = entry.getKey();
                String name = fullTrigger.substring(1, fullTrigger.length() - 1);
                String safeName = getSafeName(name);
                String unicodeStr = entry.getValue();

                FontProvider provider = new FontProvider();
                provider.file = "avoutils:font/" + safeName + ".png";
                provider.chars.add(unicodeStr);
                defaultFontConfig.providers.add(provider);
            }
        }

        // Read twemoji config, map to BMP PUA range, and write
        if (standardFontConfig != null && standardFontConfig.providers != null) {
            for (FontProvider prov : standardFontConfig.providers) {
                FontProvider defaultProv = new FontProvider(prov.type, prov.file, prov.ascent, prov.height);

                for (String row : prov.chars) {
                    StringBuilder sbDefault = new StringBuilder();
                    int i = 0;
                    while (i < row.length()) {
                        int codePoint = row.codePointAt(i);
                        int charCount = Character.charCount(codePoint);
                        if (codePoint > 32) {
                            String puaStr = standardCharToPua.get(codePoint);
                            if (puaStr != null) {
                                sbDefault.append(puaStr);
                            } else {
                                sbDefault.append("\u0000");
                            }
                        } else {
                            sbDefault.append(row, i, i + charCount);
                        }
                        i += charCount;
                    }
                    defaultProv.chars.add(sbDefault.toString());
                }
                defaultFontConfig.providers.add(defaultProv);
            }
            // Clear reference to allow garbage collection
            standardFontConfig = null;
        }

        Files.writeString(defaultFontPath, GSON.toJson(defaultFontConfig));
    }

    private void downloadTwemojiPack() {
        if (Files.exists(twemojiPath)) {
            return;
        }

        Path tempPath = twemojiPath.resolveSibling("avoutils-twemoji.tmp");
        AvoUtilsMod.LOGGER.info("Downloading Twemoji resource pack for high-quality color emojis...");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TWEMOJI_ZIP_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "AvoUtils Mod")
                    .build();
            HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 200) {
                Files.createDirectories(twemojiPath.getParent());
                try (InputStream in = response.body()) {
                    Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
                }

                sanitizeTwemojiPack(tempPath, twemojiPath);
                Files.deleteIfExists(tempPath);

                AvoUtilsMod.LOGGER.info("Twemoji resource pack downloaded and sanitized successfully.");
            } else {
                AvoUtilsMod.LOGGER.error("Failed to download Twemoji pack. HTTP code: " + response.statusCode());
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Error downloading Twemoji resource pack", e);
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
        }
    }

    private void sanitizeTwemojiPack(Path source, Path destination) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(source));
                ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(destination))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (name.equals("assets/minecraft/font/default.json")) {
                    name = TWEMOJI_RAW_JSON_PATH;
                }

                if (name.equals("pack.mcmeta")) {
                    String raw = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    try {
                        JsonObject rootObj = JsonParser.parseString(raw).getAsJsonObject();
                        if (rootObj.has("pack")) {
                            JsonObject packObj = rootObj.getAsJsonObject("pack");
                            int fmt = packFormat;
                            packObj.addProperty("pack_format", fmt);
                            packObj.addProperty("min_format", fmt);
                            packObj.addProperty("max_format", fmt);
                            packObj.remove("supported_formats");
                        }
                        raw = GSON.toJson(rootObj);
                    } catch (Exception ignored) {
                    }

                    ZipEntry newEntry = new ZipEntry(name);
                    zos.putNextEntry(newEntry);
                    zos.write(raw.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                    continue;
                }

                ZipEntry newEntry = new ZipEntry(name);
                zos.putNextEntry(newEntry);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                zos.closeEntry();
            }
        }
    }

    private void enableResourcePacks() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null)
            return;

        List<String> packsToEnable = List.of(TWEMOJI_PACK_NAME, CUSTOM_EMOJIS_PACK_NAME);
        try {
            client.getResourcePackManager().scanPacks();
            Collection<String> enabled = new ArrayList<>(client.getResourcePackManager().getEnabledIds());
            boolean modified = false;
            for (String packId : packsToEnable) {
                if (client.getResourcePackManager().getProfile(packId) != null && !enabled.contains(packId)) {
                    enabled.add(packId);
                    modified = true;
                }
            }
            if (modified) {
                client.getResourcePackManager().setEnabledProfiles(enabled);
                client.options.resourcePacks.clear();
                client.options.resourcePacks.addAll(enabled);
                client.options.write();
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Failed to enable emojis resource packs", e);
        }
    }

    private String getSafeName(String name) {
        return SAFE_NAME_PATTERN.matcher(name).replaceAll("_").toLowerCase();
    }


    private void downloadImage(String imageUrl, Path destination) throws IOException, InterruptedException {
        if (isLocalPath(imageUrl)) {
            Path srcPath = parsePath(imageUrl);
            Files.createDirectories(destination.getParent());
            Files.copy(srcPath, destination, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "AvoUtils Mod")
                .build();
        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP status " + response.statusCode() + " for Image URL: " + imageUrl);
        }
        Files.createDirectories(destination.getParent());
        try (InputStream in = response.body()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean isLocalPath(String urlString) {
        return urlString.startsWith("file://") || urlString.startsWith("/") || urlString.contains(":\\")
                || urlString.startsWith("file:/");
    }

    private Path parsePath(String urlString) {
        if (urlString.startsWith("file://") || urlString.startsWith("file:/")) {
            return Path.of(URI.create(urlString));
        }
        return Path.of(urlString);
    }

    private static int resolvePackFormat() {
        try {
            Object gameVersion = Class.forName("net.minecraft.SharedConstants")
                    .getMethod("getGameVersion")
                    .invoke(null);
            Class<?> resourceTypeClass = Class.forName("net.minecraft.resource.ResourceType");
            Object clientResources = resourceTypeClass.getField("CLIENT_RESOURCES").get(null);
            return (int) gameVersion.getClass()
                    .getMethod("getResourceVersion", resourceTypeClass)
                    .invoke(gameVersion, clientResources);
        } catch (Throwable t) {
            return 75; // Fallback
        }
    }

    // ── Font JSON structures ─────────────────────────────────────────────

    private static class FontConfig {
        public List<FontProvider> providers = new ArrayList<>();
    }

    private static class FontProvider {
        public String type = "bitmap";
        public String file;
        public int ascent = 7;
        public int height = 8;
        public List<String> chars = new ArrayList<>();

        public FontProvider() {
        }

        public FontProvider(String type, String file, int ascent, int height) {
            this.type = type;
            this.file = file;
            this.ascent = ascent;
            this.height = height;
        }
    }

    // ── pack.mcmeta structures ───────────────────────────────────────────

    private record PackMetadata(PackInfo pack) {
    }

    private record PackInfo(int pack_format, int min_format, int max_format, String description) {
        public PackInfo(int packFormat, String description) {
            this(packFormat, packFormat, packFormat, description);
        }
    }
}
