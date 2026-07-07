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
import info.avicia.avoutils.core.util.WynnPillUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class EmojiFeature implements AvoFeature {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("[^a-zA-Z0-9_.-]");
    private static final String TWEMOJI_ZIP_URL = "https://github.com/AmberWat/PixelTwemojiMC-18/releases/download/v8.0/PixelTwemojiMC-18.zip";
    private static final String TWEMOJI_RAW_JSON_PATH = "assets/twemoji/font/emoji_raw.json";
    private static final String TWEMOJI_SHORTCODES_JSON_PATH = "assets/emoji_shortcodes/lang/en_us.json";

    private final Path twemojiPath = FabricLoader.getInstance().getGameDir().resolve("avoutils/emojis/avoutils-twemoji.zip");
    private final Path packDir = FabricLoader.getInstance().getGameDir().resolve("avoutils/emojis/avoutils-emojis");
    private final int packFormat = resolvePackFormat();

    private final Map<String, String> standardEmojis = new HashMap<>();
    private final Map<Integer, String> standardCharToPua = new HashMap<>();
    private final Map<String, String> customEmojis = new ConcurrentHashMap<>();
    private final Map<String, String> safeNameCache = new ConcurrentHashMap<>();

    private ModConfig config;

    private final AtomicBoolean downloadingTwemoji = new AtomicBoolean(false);
    private final AtomicBoolean loadingEmojis = new AtomicBoolean(false);

    private volatile EmojiTrie activeTrie = new EmojiTrie();
    private volatile boolean packsLoaded = false;
    private FontConfig standardFontConfig;

    public EmojiTrie getActiveTrie() {
        return activeTrie;
    }

    public boolean isEnabled() {
        return config != null && config.emojiEnabled;
    }

    @Override
    public void initialize(ModConfig config) {
        this.config = config;

        try {
            createResourcePackStructure();
        } catch (IOException e) {
            AvoUtilsMod.LOGGER.error("Failed to create emoji resource pack folders", e);
        }

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            CompletableFuture.runAsync(() -> {
                boolean changed = !Files.exists(twemojiPath);
                downloadTwemojiPack();
                loadStandardEmojiResources();
                loadAndCacheEmojis();
                if (changed) {
                    client.execute(client::reloadResources);
                }
                packsLoaded = isEnabled();
            });
        });

        registerCommand();
    }

    private static final Formatting PILL_BG = Formatting.DARK_AQUA;
    private static final Formatting PILL_FG = Formatting.DARK_GRAY;
    private static final Formatting ARROW_COLOR = Formatting.GRAY;

    private static MutableText createPillPrefix() {
        return WynnPillUtil.create("AvoUtils", PILL_BG, PILL_FG)
                .append(Text.literal(" \u203A\u203A ").formatted(ARROW_COLOR));
    }

    private void registerCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("avoemojis")
                            .executes(context -> {
                                config.emojiEnabled = !config.emojiEnabled;
                                config.save();

                                MinecraftClient client = MinecraftClient.getInstance();
                                Formatting statusColor = config.emojiEnabled ? Formatting.GREEN : Formatting.RED;
                                String statusWord = config.emojiEnabled ? "enabled" : "disabled";

                                MutableText message = createPillPrefix()
                                        .append(Text.literal("Emojis are now ").formatted(Formatting.GRAY))
                                        .append(Text.literal(statusWord).formatted(statusColor))
                                        .append(Text.literal(".").formatted(Formatting.GRAY));

                                if (client.player != null) {
                                    client.player.sendMessage(message, false);
                                    if (config.emojiEnabled && !packsLoaded) {
                                        client.execute(client::reloadResources);
                                        packsLoaded = true;
                                    }
                                }
                                return 1;
                            })
            );
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
        if (entry == null)
            return;

        try (InputStream is = zip.getInputStream(entry);
                InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            standardFontConfig = GSON.fromJson(isr, FontConfig.class);
            if (standardFontConfig != null && standardFontConfig.providers != null) {
                for (FontProvider prov : standardFontConfig.providers) {
                    walkProviderChars(prov, (codePoint, _charCount) -> {
                        standardCharToPua.putIfAbsent(codePoint,
                                String.valueOf((char) (0xE200 + standardCharToPua.size())));
                    });
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
                if (value.isEmpty())
                    continue;
                StringBuilder puaBuilder = new StringBuilder();
                walkStringCodePoints(value, codePoint -> {
                    String puaChar = standardCharToPua.get(codePoint);
                    if (puaChar != null) {
                        puaBuilder.append(puaChar);
                    }
                });
                if (puaBuilder.length() > 0) {
                    standardEmojis.put(shortcode, puaBuilder.toString());
                }
            }
            AvoUtilsMod.LOGGER.info("Successfully loaded {} standard Twemoji shortcodes from ZIP.",
                    standardEmojis.size());
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Failed to parse Twemoji shortcodes from ZIP", e);
        }
    }

    private void loadAndCacheEmojis() {
        if (!loadingEmojis.compareAndSet(false, true))
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

            if (!allEmojis.isEmpty()) {
                Path texturesDir = packDir.resolve("assets/avoutils/textures/font");
                Files.createDirectories(texturesDir);

                // Download images in parallel using a dedicated I/O thread pool
                List<Map.Entry<String, String>> entries = new ArrayList<>(allEmojis.entrySet());
                Set<String> downloadedEmojis = ConcurrentHashMap.newKeySet();
                ExecutorService downloadExecutor = Executors.newFixedThreadPool(8);

                try {
                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (Map.Entry<String, String> entry : entries) {
                        futures.add(CompletableFuture.runAsync(() -> {
                            String emojiName = entry.getKey();
                            String imageUrl = entry.getValue();
                            String safeName = getSafeName(emojiName);
                            safeNameCache.put(emojiName, safeName);
                            Path imagePath = texturesDir.resolve(safeName + ".png");

                            try {
                                if (!Files.exists(imagePath)) {
                                    downloadImage(imageUrl, imagePath);
                                }
                                downloadedEmojis.add(emojiName);
                            } catch (IOException e) {
                                AvoUtilsMod.LOGGER.error("Failed to download emoji image for {} from {}",
                                        emojiName, imageUrl, e);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }, downloadExecutor));
                    }
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                } finally {
                    downloadExecutor.shutdown();
                    try {
                        downloadExecutor.awaitTermination(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                // Rebuild Unicode mapping
                List<String> sortedNames = new ArrayList<>(downloadedEmojis);
                Collections.sort(sortedNames);

                synchronized (customEmojis) {
                    customEmojis.clear();
                    char currentUnicode = '\uF800';
                    for (String name : sortedNames) {
                        customEmojis.put(":" + name + ":", String.valueOf(currentUnicode));
                        currentUnicode++;
                    }
                }
                AvoUtilsMod.LOGGER.info("Successfully loaded {} custom emojis.", downloadedEmojis.size());
            }

            rebuildActiveEmojis();
            writeFontJson();

        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Error occurred while loading emojis", e);
        } finally {
            loadingEmojis.set(false);
        }
    }

    private void rebuildActiveEmojis() {
        EmojiTrie newTrie = new EmojiTrie();
        for (Map.Entry<String, String> entry : standardEmojis.entrySet()) {
            newTrie.insert(entry.getKey(), entry.getValue());
        }
        synchronized (customEmojis) {
            for (Map.Entry<String, String> entry : customEmojis.entrySet()) {
                newTrie.insert(entry.getKey(), entry.getValue());
            }
        }
        this.activeTrie = newTrie;
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
            PackMetadata pack = new PackMetadata(PackInfo.uniform(packFormat, "Dynamic emojis for AvoUtils"));
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
                String safeName = safeNameCache.getOrDefault(name, getSafeName(name));
                String unicodeStr = entry.getValue();

                FontProvider provider = new FontProvider();
                provider.file = "avoutils:font/" + safeName + ".png";
                provider.chars.add(unicodeStr);
                defaultFontConfig.providers.add(provider);
            }
        }

        // Map Twemoji font providers to PUA range
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
                                sbDefault.append('\u0000');
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
        }

        Files.writeString(defaultFontPath, GSON.toJson(defaultFontConfig));
    }

    private void downloadTwemojiPack() {
        if (Files.exists(twemojiPath)) {
            return;
        }
        if (!downloadingTwemoji.compareAndSet(false, true)) {
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
                AvoUtilsMod.LOGGER.error("Failed to download Twemoji pack. HTTP code: {}", response.statusCode());
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Error downloading Twemoji resource pack", e);
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
        } finally {
            downloadingTwemoji.set(false);
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
                            packObj.addProperty("pack_format", packFormat);
                            packObj.addProperty("min_format", packFormat);
                            packObj.addProperty("max_format", packFormat);
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

    private String getSafeName(String name) {
        return SAFE_NAME_PATTERN.matcher(name).replaceAll("_").toLowerCase();
    }

    private void downloadImage(String imageUrl, Path destination) throws IOException, InterruptedException {
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

    public String replaceUnicodeEmojisWithPua(String text) {
        if (text == null || text.isEmpty() || standardCharToPua.isEmpty()) {
            return text;
        }
        StringBuilder sb = null;
        int i = 0;
        int len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);
            String replacement = standardCharToPua.get(cp);
            if (replacement != null) {
                if (sb == null) {
                    sb = new StringBuilder(len);
                    sb.append(text, 0, i);
                }
                sb.append(replacement);
            } else if (sb != null) {
                sb.append(text, i, i + charCount);
            }
            i += charCount;
        }
        return sb != null ? sb.toString() : text;
    }

    private static void walkProviderChars(FontProvider prov, BiConsumer<Integer, Integer> consumer) {
        for (String row : prov.chars) {
            walkStringCodePoints(row, codePoint -> {
                if (codePoint > 32) {
                    consumer.accept(codePoint, Character.charCount(codePoint));
                }
            });
        }
    }

    private static void walkStringCodePoints(String str, java.util.function.IntConsumer consumer) {
        int i = 0;
        while (i < str.length()) {
            int codePoint = str.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if (codePoint > 32) {
                consumer.accept(codePoint);
            }
            i += charCount;
        }
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
        static PackInfo uniform(int format, String description) {
            return new PackInfo(format, format, format, description);
        }
    }
}
