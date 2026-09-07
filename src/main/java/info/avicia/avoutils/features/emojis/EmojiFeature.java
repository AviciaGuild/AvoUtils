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
import info.avicia.avoutils.features.emojis.animation.AnimatedImageDecoder;
import info.avicia.avoutils.features.emojis.animation.AnimationFrame;
import info.avicia.avoutils.features.emojis.animation.AnimationMeta;
import info.avicia.avoutils.features.emojis.models.FontConfig;
import info.avicia.avoutils.features.emojis.models.FontProvider;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import java.io.ByteArrayInputStream;
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
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
    private final Path packDir;
    private final int packFormat;

    private final Map<String, String> customEmojis = new ConcurrentHashMap<>();
    private final Map<Integer, AnimationMeta> animatedEmojis = new ConcurrentHashMap<>();
    private final Map<String, AnimationCacheEntry> animationCache = new ConcurrentHashMap<>();
    private final Path animationCachePath;
    private final Path manifestPath;
    private ModConfig config;

    private final AtomicBoolean loadingEmojis = new AtomicBoolean(false);

    private volatile EmojiTrie activeTrie = new EmojiTrie();
    private volatile boolean packsLoaded = false;

    public record AnimationCacheEntry(int frameCount, int[] frameDelays) {
    }

    public record EmojiManifest(String hash, Map<String, String> urls) {
    }

    public EmojiFeature() {
        this(resolvePackFormat());
    }

    private EmojiFeature(int packFormat) {
        this(
                new TwemojiManager(FabricLoader.getInstance().getGameDir(), packFormat),
                FabricLoader.getInstance().getGameDir().resolve("avoutils/emojis/avoutils-emojis"),
                packFormat
        );
    }

    EmojiFeature(TwemojiManager twemojiManager, Path packDir, int packFormat) {
        this.twemojiManager = twemojiManager;
        this.packDir = packDir;
        this.packFormat = packFormat;
        this.animationCachePath = packDir.resolve("assets/avoutils/animations.json");
        this.manifestPath = packDir.resolve("assets/avoutils/manifest.json");
    }

    void setConfig(ModConfig config) {
        this.config = config;
    }

    Map<String, String> getCustomEmojis() {
        return customEmojis;
    }

    Map<String, AnimationCacheEntry> getAnimationCache() {
        return animationCache;
    }

    boolean writeFontJsonForTesting() throws IOException {
        return writeFontJson();
    }

    Path getManifestPath() {
        return manifestPath;
    }

    EmojiManifest loadManifestForTesting() {
        return loadManifest();
    }

    void saveManifestForTesting(EmojiManifest manifest) {
        saveManifest(manifest);
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

    public int getAnimatedFrameCodePoint(int codePoint) {
        if (!isEnabled()) {
            return codePoint;
        }
        AnimationMeta meta = animatedEmojis.get(codePoint);
        if (meta != null) {
            return meta.getActiveCodePoint(System.currentTimeMillis());
        }
        return codePoint;
    }

    public Map<Integer, AnimationMeta> getAnimatedEmojis() {
        return animatedEmojis;
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
        int prefixLen = prefix.length();
        List<String> matches = new ArrayList<>();

        if (customKeys != null) {
            for (String key : customKeys) {
                if (key.length() >= prefixLen && key.regionMatches(true, 0, prefix, 0, prefixLen)) {
                    matches.add(key);
                }
            }
        }
        Collections.sort(matches);

        if (matches.size() >= 50) {
            return matches.subList(0, 50);
        }

        List<String> twemojiMatches = new ArrayList<>();
        if (standardKeys != null) {
            for (String key : standardKeys) {
                if (customKeys != null && customKeys.contains(key)) {
                    continue;
                }
                if (key.length() >= prefixLen && key.regionMatches(true, 0, prefix, 0, prefixLen)) {
                    twemojiMatches.add(key);
                }
            }
        }
        Collections.sort(twemojiMatches);

        int remaining = 50 - matches.size();
        if (twemojiMatches.size() > remaining) {
            matches.addAll(twemojiMatches.subList(0, remaining));
        } else {
            matches.addAll(twemojiMatches);
        }

        return matches;
    }

    public String getEmojiReplacement(String shortcode) {
        if (shortcode == null) return null;
        String custom = customEmojis.get(shortcode);
        if (custom != null) {
            return custom;
        }
        String twemoji = twemojiManager.standardEmojis.get(shortcode);
        if (twemoji != null) {
            return twemoji;
        }
        if (!shortcode.startsWith(":") || !shortcode.endsWith(":")) {
            String wrapped = ":" + shortcode + ":";
            custom = customEmojis.get(wrapped);
            if (custom != null) {
                return custom;
            }
            return twemojiManager.standardEmojis.get(wrapped);
        }
        return null;
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
                    reloadResourcesWithPacks(client);
                }
                packsLoaded = isEnabled();
            });
        });
    }

    public CompletableFuture<Void> reloadEmojis() {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.deleteIfExists(manifestPath);
            } catch (Exception ignored) {
            }
            twemojiManager.loadResources();
            loadAndCacheEmojis();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                reloadResourcesWithPacks(client);
            }
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

    public static void reloadResourcesWithPacks(MinecraftClient client) {
        if (client == null) return;
        client.execute(() -> {
            if (client.getResourcePackManager() == null) return;
            client.getResourcePackManager().scanPacks();
            List<String> enabled = new ArrayList<>(client.getResourcePackManager().getEnabledIds());
            if (client.getResourcePackManager().hasProfile("avoutils/twemoji") && !enabled.contains("avoutils/twemoji")) {
                enabled.add("avoutils/twemoji");
            }
            if (client.getResourcePackManager().hasProfile("avoutils/emojis") && !enabled.contains("avoutils/emojis")) {
                enabled.add("avoutils/emojis");
            }
            client.getResourcePackManager().setEnabledProfiles(enabled);
            client.reloadResources();
        });
    }

    public void ensurePacksLoaded() {
        if (config != null && config.emojiEnabled && !packsLoaded) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                reloadResourcesWithPacks(client);
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
            loadAnimationCache();

            Map<String, String> allEmojis = new HashMap<>();
            String currentHash = "";

            try (InputStream is = EmojiFeature.class.getResourceAsStream("/assets/avoutils/custom_emojis.json")) {
                if (is != null) {
                    byte[] rawBytes = is.readAllBytes();
                    try {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        currentHash = HexFormat.of().formatHex(digest.digest(rawBytes));
                    } catch (Exception ignored) {
                    }

                    try (InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(rawBytes), StandardCharsets.UTF_8)) {
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

                EmojiManifest existingManifest = loadManifest();
                boolean manifestChanged = existingManifest == null || !Objects.equals(currentHash, existingManifest.hash());
                Map<String, String> cachedUrls = (existingManifest != null && existingManifest.urls() != null)
                        ? existingManifest.urls()
                        : Collections.emptyMap();

                // Clean up any old images that were removed from custom_emojis.json
                for (String cachedName : new ArrayList<>(cachedUrls.keySet())) {
                    if (!allEmojis.containsKey(cachedName)) {
                        try {
                            Files.deleteIfExists(texturesDir.resolve(safeNameFor(cachedName) + ".png"));
                        } catch (Exception ignored) {
                        }
                        animationCache.remove(cachedName);
                        changed = true;
                    }
                }

                Map<String, String> currentUrls = new ConcurrentHashMap<>();
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
                                boolean needsProcessing = false;
                                BufferedImage existingImg = null;

                                boolean urlChanged = cachedUrls.containsKey(emojiName)
                                        && !Objects.equals(cachedUrls.get(emojiName), imageUrl);

                                if (!Files.exists(imagePath) || urlChanged) {
                                    needsProcessing = true;
                                } else {
                                    try {
                                        existingImg = ImageIO.read(imagePath.toFile());
                                        if (existingImg == null || existingImg.getWidth() != 32 || existingImg.getHeight() % 32 != 0) {
                                            needsProcessing = true;
                                        } else if (imageUrl.contains(".gif") && (!animationCache.containsKey(emojiName) || existingImg.getHeight() == 32)) {
                                            needsProcessing = true;
                                        } else if (animationCache.containsKey(emojiName) && existingImg.getHeight() != animationCache.get(emojiName).frameCount() * 32) {
                                            needsProcessing = true;
                                        }
                                    } catch (Exception e) {
                                        needsProcessing = true;
                                    }
                                }

                                if (needsProcessing) {
                                    try {
                                        AnimationCacheEntry anim = processAndSaveEmojiImage(imageUrl, imagePath);
                                        if (anim != null) {
                                            animationCache.put(emojiName, anim);
                                        } else {
                                            animationCache.remove(emojiName);
                                        }
                                        anyNewImage.set(true);
                                        currentUrls.put(emojiName, imageUrl);
                                    } catch (IOException downloadEx) {
                                        // Offline fallback: if image exists on disk but was non-square, normalize it locally
                                        if (existingImg != null && (existingImg.getWidth() != 32 || existingImg.getHeight() % 32 != 0)) {
                                            BufferedImage normalized = AnimatedImageDecoder.scaleToTarget(existingImg, 32);
                                            Path temp = imagePath.resolveSibling(imagePath.getFileName().toString() + "." + UUID.randomUUID().toString().substring(0, 8) + ".tmp");
                                            try {
                                                ImageIO.write(normalized, "png", temp.toFile());
                                                Files.move(temp, imagePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                                                anyNewImage.set(true);
                                                currentUrls.put(emojiName, imageUrl);
                                            } finally {
                                                try {
                                                    Files.deleteIfExists(temp);
                                                } catch (Exception ignored) {
                                                }
                                            }
                                        } else {
                                            throw downloadEx;
                                        }
                                    }
                                } else {
                                    currentUrls.put(emojiName, imageUrl);
                                    if (existingImg != null && existingImg.getHeight() > existingImg.getWidth()) {
                                        int frameCount = existingImg.getHeight() / existingImg.getWidth();
                                        if (!animationCache.containsKey(emojiName)) {
                                            int[] delays = new int[frameCount];
                                            Arrays.fill(delays, 100);
                                            animationCache.put(emojiName, new AnimationCacheEntry(frameCount, delays));
                                            anyNewImage.set(true);
                                        }
                                        if (AnimatedImageDecoder.normalizeFrameAdvances(existingImg, frameCount)) {
                                            Path temp = imagePath.resolveSibling(imagePath.getFileName().toString() + "." + UUID.randomUUID().toString().substring(0, 8) + ".tmp");
                                            try {
                                                ImageIO.write(existingImg, "png", temp.toFile());
                                                Files.move(temp, imagePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                                                anyNewImage.set(true);
                                            } finally {
                                                try {
                                                    Files.deleteIfExists(temp);
                                                } catch (Exception ignored) {
                                                }
                                            }
                                        }
                                    }
                                }
                                downloadedEmojis.add(emojiName);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } catch (Exception e) {
                                AvoUtilsMod.LOGGER.error("Failed to process emoji image for {} from {}",
                                        emojiName, imageUrl, e);
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

                if (anyNewImage.get() || manifestChanged) {
                    saveManifest(new EmojiManifest(currentHash, currentUrls));
                    saveAnimationCache();
                }
                changed = changed || anyNewImage.get() || manifestChanged;

                List<String> sortedNames = new ArrayList<>(downloadedEmojis);
                Collections.sort(sortedNames);

                Map<String, String> newCustomEmojis = new HashMap<>();
                Map<Integer, AnimationMeta> newAnimatedEmojis = new HashMap<>();
                int currentCodePoint = 0xF0000;
                for (String name : sortedNames) {
                    newCustomEmojis.put(":" + name + ":", Character.toString(currentCodePoint));
                    AnimationCacheEntry anim = animationCache.get(name);
                    if (anim != null && anim.frameCount() > 1) {
                        AnimationMeta meta = new AnimationMeta(currentCodePoint, anim.frameDelays());
                        newAnimatedEmojis.put(currentCodePoint, meta);
                        currentCodePoint += anim.frameCount();
                    } else {
                        currentCodePoint++;
                    }
                }

                synchronized (customEmojis) {
                    customEmojis.clear();
                    customEmojis.putAll(newCustomEmojis);
                    animatedEmojis.putAll(newAnimatedEmojis);
                    animatedEmojis.keySet().removeIf(k -> !newAnimatedEmojis.containsKey(k));
                }
                AvoUtilsMod.LOGGER.info("Successfully loaded {} custom emojis ({} animated).",
                        downloadedEmojis.size(), animatedEmojis.size());
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
    }

    private boolean writeFontJson() throws IOException {
        Path defaultFontPath = packDir.resolve("assets/minecraft/font/default.json");
        Files.createDirectories(defaultFontPath.getParent());

        FontConfig defaultFontConfig = new FontConfig();

        synchronized (customEmojis) {
            List<Map.Entry<String, String>> sortedEntries = new ArrayList<>(customEmojis.entrySet());
            sortedEntries.sort(Map.Entry.comparingByKey());

            for (Map.Entry<String, String> entry : sortedEntries) {
                String fullTrigger = entry.getKey();
                String name = fullTrigger.substring(1, fullTrigger.length() - 1);
                String safeName = safeNameFor(name);
                String unicodeStr = entry.getValue();
                int baseCp = unicodeStr.codePointAt(0);

                FontProvider provider = new FontProvider();
                provider.file = "avoutils:font/" + safeName + ".png";

                AnimationMeta anim = animatedEmojis.get(baseCp);
                if (anim != null && anim.frameCount() > 1) {
                    for (int f = 0; f < anim.frameCount(); f++) {
                        provider.chars.add(Character.toString(baseCp + f));
                    }
                } else {
                    provider.chars.add(unicodeStr);
                }
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
            try {
                Files.writeString(tempPath, newJson, StandardCharsets.UTF_8);
                Files.move(tempPath, defaultFontPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (Exception ignored) {
                }
            }
        }
        return contentChanged;
    }

    // ── Image download ───────────────────────────────────────────────────

    // Sanitizes an emoji name for use as a resource-pack file name
    static String safeNameFor(String name) {
        return SAFE_NAME_PATTERN.matcher(name).replaceAll("_").toLowerCase(Locale.ROOT);
    }

    private void loadAnimationCache() {
        if (!Files.exists(animationCachePath)) {
            return;
        }
        try {
            String json = Files.readString(animationCachePath, StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, AnimationCacheEntry>>() {}.getType();
            Map<String, AnimationCacheEntry> map = GSON.fromJson(json, type);
            if (map != null) {
                animationCache.putAll(map);
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Failed to load animation cache from {}", animationCachePath, e);
        }
    }

    private void saveAnimationCache() {
        Path temp = animationCachePath.resolveSibling("animations.json.tmp");
        try {
            Files.createDirectories(animationCachePath.getParent());
            String json = GSON.toJson(animationCache);
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            Files.move(temp, animationCachePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Failed to save animation cache to {}", animationCachePath, e);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
            }
        }
    }

    private EmojiManifest loadManifest() {
        if (!Files.exists(manifestPath)) {
            return null;
        }
        try {
            String json = Files.readString(manifestPath, StandardCharsets.UTF_8);
            return GSON.fromJson(json, EmojiManifest.class);
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Failed to load emoji manifest from {}", manifestPath, e);
            return null;
        }
    }

    private void saveManifest(EmojiManifest manifest) {
        Path temp = manifestPath.resolveSibling("manifest.json.tmp");
        try {
            Files.createDirectories(manifestPath.getParent());
            String json = GSON.toJson(manifest);
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            Files.move(temp, manifestPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Failed to save emoji manifest to {}", manifestPath, e);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
            }
        }
    }

    private AnimationCacheEntry processAndSaveEmojiImage(String imageUrl, Path destination) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "AvoUtils Mod")
                .build();
        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP status " + response.statusCode() + " for Image URL: " + imageUrl);
        }
        byte[] bytes;
        try (InputStream in = response.body()) {
            bytes = in.readAllBytes();
        }
        Files.createDirectories(destination.getParent());
        Path tempPath = destination.resolveSibling(destination.getFileName().toString() + "." + UUID.randomUUID().toString().substring(0, 8) + ".tmp");

        try {
            List<AnimationFrame> frames = AnimatedImageDecoder.decode(bytes);
            if (frames.isEmpty()) {
                throw new IOException("Failed to decode image data for " + imageUrl);
            }

            AnimationCacheEntry result;
            if (frames.size() > 1) {
                BufferedImage sheet = AnimatedImageDecoder.stitchVertically(frames);
                ImageIO.write(sheet, "png", tempPath.toFile());
                int[] delays = frames.stream().mapToInt(AnimationFrame::delayMs).toArray();
                result = new AnimationCacheEntry(frames.size(), delays);
            } else {
                ImageIO.write(frames.get(0).image(), "png", tempPath.toFile());
                result = null;
            }

            Files.move(tempPath, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return result;
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (Exception ignored) {
            }
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
