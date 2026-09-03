package info.avicia.avoutils.features.emojis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.util.WynnPillUtil;
import info.avicia.avoutils.features.emojis.models.FontConfig;
import info.avicia.avoutils.features.emojis.models.FontProvider;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EmojiFeature implements AvoFeature {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("[^a-zA-Z0-9_.-]");

    private final TwemojiManager twemojiManager;
    private final Path packDir = FabricLoader.getInstance().getGameDir().resolve("avoutils/emojis/avoutils-emojis");
    private final int packFormat = resolvePackFormat();

    private final Map<String, String> customEmojis = new ConcurrentHashMap<>();
    private ModConfig config;

    private final AtomicBoolean loadingEmojis = new AtomicBoolean(false);

    private volatile EmojiTrie activeTrie = new EmojiTrie();
    private volatile boolean packsLoaded = false;

    public EmojiFeature() {
        this.twemojiManager = new TwemojiManager(
                FabricLoader.getInstance().getGameDir(), packFormat);
    }

    public EmojiTrie getActiveTrie() {
        return activeTrie;
    }

    public boolean isEnabled() {
        return config != null && config.emojiEnabled;
    }

    public boolean isAutocompleteEnabled() {
        return config != null && config.emojiEnabled && config.emojiAutocompleteEnabled;
    }

    public record AutocompletePrefix(int startIndex, String prefix) {
    }

    public static boolean isValidShortcodeChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_' || c == '+' || c == '-';
    }

    static boolean isAllowedBeforeShortcodeColon(char c) {
        return Character.isWhitespace(c)
                || c == ':'
                || c == '(' || c == '[' || c == '{'
                || c == '"' || c == '\'';
    }

    public static AutocompletePrefix findAutocompletePrefix(String textBeforeCursor) {
        if (textBeforeCursor == null || textBeforeCursor.isEmpty()) {
            return null;
        }

        int lastColon = textBeforeCursor.lastIndexOf(':');
        if (lastColon == -1) {
            return null;
        }

        String afterColon = textBeforeCursor.substring(lastColon + 1);
        for (int i = 0; i < afterColon.length(); i++) {
            char c = afterColon.charAt(i);
            if (!isValidShortcodeChar(c)) {
                return null;
            }
        }

        if (!afterColon.isEmpty()) {
            if (lastColon > 0 && !isAllowedBeforeShortcodeColon(textBeforeCursor.charAt(lastColon - 1))) {
                return null;
            }
            return new AutocompletePrefix(lastColon, ":" + afterColon);
        }

        // afterColon is empty: cursor is directly after the colon
        if (lastColon == 0 || isAllowedBeforeShortcodeColon(textBeforeCursor.charAt(lastColon - 1))) {
            return new AutocompletePrefix(lastColon, ":");
        }

        return null;
    }

    public List<String> getMatchingShortcodes(String prefix) {
        synchronized (customEmojis) {
            return matchShortcodes(customEmojis.keySet(), twemojiManager.standardEmojis.keySet(), prefix);
        }
    }

    static List<String> matchShortcodes(Set<String> customKeys, Set<String> standardKeys, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return Collections.emptyList();
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();

        if (customKeys != null) {
            for (String key : customKeys) {
                if (key.toLowerCase(Locale.ROOT).startsWith(lower)) {
                    matches.add(key);
                }
            }
        }
        Collections.sort(matches);

        List<String> twemojiMatches = new ArrayList<>();
        if (standardKeys != null) {
            for (String key : standardKeys) {
                if (key.toLowerCase(Locale.ROOT).startsWith(lower)) {
                    twemojiMatches.add(key);
                }
            }
        }
        Collections.sort(twemojiMatches);
        matches.addAll(twemojiMatches);

        if (matches.size() > 50) {
            return matches.subList(0, 50);
        }
        return matches;
    }

    public String getEmojiReplacement(String shortcode) {
        String custom = customEmojis.get(shortcode);
        if (custom != null) {
            return custom;
        }
        return twemojiManager.standardEmojis.get(shortcode);
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
                boolean twemojiChanged = !twemojiManager.exists();
                twemojiManager.download();
                twemojiManager.loadResources();
                boolean customChanged = loadAndCacheEmojis();
                if (twemojiChanged || customChanged) {
                    client.execute(client::reloadResources);
                }
                packsLoaded = isEnabled();
            });
        });
    }

    public void toggleEmojis() {
        config.emojiEnabled = !config.emojiEnabled;
        config.save();

        MinecraftClient client = MinecraftClient.getInstance();
        Formatting statusColor = config.emojiEnabled ? Formatting.GREEN : Formatting.RED;
        String statusWord = config.emojiEnabled ? "enabled" : "disabled";

        MutableText message = WynnPillUtil.createPrefixedPill("AvoUtils", false)
                .append(Text.literal("Emojis are now ").formatted(Formatting.GRAY))
                .append(Text.literal(statusWord).formatted(statusColor))
                .append(Text.literal(".").formatted(Formatting.GRAY));

        if (client.player != null) {
            client.player.sendMessage(message, false);
            ensurePacksLoaded();
        }
    }

    public void ensurePacksLoaded() {
        if (config != null && config.emojiEnabled && !packsLoaded) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.execute(client::reloadResources);
                packsLoaded = true;
            }
        }
    }

    // ── Custom emoji loading ─────────────────────────────────────────────

    private boolean loadAndCacheEmojis() {
        if (!loadingEmojis.compareAndSet(false, true))
            return false;

        boolean changed = false;
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

                List<Map.Entry<String, String>> entries = new ArrayList<>(allEmojis.entrySet());
                Set<String> downloadedEmojis = ConcurrentHashMap.newKeySet();
                AtomicBoolean anyNewImage = new AtomicBoolean(false);
                ExecutorService downloadExecutor = Executors.newFixedThreadPool(8);

                try {
                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (Map.Entry<String, String> entry : entries) {
                        futures.add(CompletableFuture.runAsync(() -> {
                            String emojiName = entry.getKey();
                            String imageUrl = entry.getValue();
                            String safeName = safeNameFor(emojiName);
                            Path imagePath = texturesDir.resolve(safeName + ".png");

                            try {
                                if (!Files.exists(imagePath)) {
                                    downloadImage(imageUrl, imagePath);
                                    anyNewImage.set(true);
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
                    return false;
                }

                List<String> sortedNames = new ArrayList<>(downloadedEmojis);
                Collections.sort(sortedNames);

                synchronized (customEmojis) {
                    customEmojis.clear();
                    int currentCodePoint = 0xF0000;
                    for (String name : sortedNames) {
                        customEmojis.put(":" + name + ":", Character.toString(currentCodePoint));
                        currentCodePoint++;
                    }
                }
                AvoUtilsMod.LOGGER.info("Successfully loaded {} custom emojis.", downloadedEmojis.size());
                changed = anyNewImage.get();
            }

            rebuildActiveEmojis();
            boolean fontChanged = writeFontJson();
            return changed || fontChanged;

        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Error occurred while loading emojis", e);
            return false;
        } finally {
            loadingEmojis.set(false);
        }
    }

    private void rebuildActiveEmojis() {
        this.activeTrie = buildTrie(twemojiManager.standardEmojis, customEmojis);
    }

    static EmojiTrie buildTrie(Map<String, String> standardEmojis, Map<String, String> customEmojis) {
        EmojiTrie newTrie = new EmojiTrie();
        for (Map.Entry<String, String> entry : standardEmojis.entrySet()) {
            newTrie.insert(entry.getKey(), entry.getValue());
        }
        synchronized (customEmojis) {
            for (Map.Entry<String, String> entry : customEmojis.entrySet()) {
                newTrie.insert(entry.getKey(), entry.getValue());
            }
        }
        return newTrie;
    }

    // ── Resource pack structure ──────────────────────────────────────────

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

        if (!twemojiManager.exists()) {
            Files.deleteIfExists(packDir.resolve("assets/minecraft/font/default.json"));
        }
    }

    private boolean writeFontJson() throws IOException {
        Path defaultFontPath = packDir.resolve("assets/minecraft/font/default.json");
        Files.createDirectories(defaultFontPath.getParent());

        FontConfig defaultFontConfig = new FontConfig();

        synchronized (customEmojis) {
            for (Map.Entry<String, String> entry : customEmojis.entrySet()) {
                String fullTrigger = entry.getKey();
                String name = fullTrigger.substring(1, fullTrigger.length() - 1);
                String safeName = safeNameFor(name);
                String unicodeStr = entry.getValue();

                FontProvider provider = new FontProvider();
                provider.file = "avoutils:font/" + safeName + ".png";
                provider.chars.add(unicodeStr);
                defaultFontConfig.providers.add(provider);
            }
        }

        FontConfig standardConfig = twemojiManager.standardFontConfig;
        if (standardConfig != null && standardConfig.providers != null) {
            for (FontProvider prov : standardConfig.providers) {
                FontProvider defaultProv = new FontProvider();
                defaultProv.type = prov.type;
                defaultProv.file = prov.file;
                defaultProv.ascent = prov.ascent;
                defaultProv.height = prov.height;

                Map<Integer, String> charToPua = twemojiManager.standardCharToPua;
                for (String row : prov.chars) {
                    StringBuilder sbDefault = new StringBuilder();
                    int i = 0;
                    while (i < row.length()) {
                        int codePoint = row.codePointAt(i);
                        int charCount = Character.charCount(codePoint);
                        if (codePoint > 32) {
                            String puaStr = charToPua.get(codePoint);
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

        String newJson = GSON.toJson(defaultFontConfig);
        boolean contentChanged = true;
        if (Files.exists(defaultFontPath)) {
            try {
                String existing = Files.readString(defaultFontPath, StandardCharsets.UTF_8);
                if (existing.equals(newJson)) {
                    contentChanged = false;
                }
            } catch (Exception ignored) {
            }
        }

        if (contentChanged) {
            Path tempPath = defaultFontPath.resolveSibling("default.json.tmp");
            Files.writeString(tempPath, newJson, StandardCharsets.UTF_8);
            Files.move(tempPath, defaultFontPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        return contentChanged;
    }

    // ── Image download ───────────────────────────────────────────────────

    // Sanitizes an emoji name for use as a resource-pack file name
    static String safeNameFor(String name) {
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

    // ── Pack format resolution ───────────────────────────────────────────

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
            return 75;
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
