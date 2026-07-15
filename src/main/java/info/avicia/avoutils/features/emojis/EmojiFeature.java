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
    private final Map<String, String> safeNameCache = new ConcurrentHashMap<>();

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
                boolean changed = !twemojiManager.exists();
                twemojiManager.download();
                twemojiManager.loadResources();
                loadAndCacheEmojis();
                if (changed) {
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
            if (config.emojiEnabled && !packsLoaded) {
                client.execute(client::reloadResources);
                packsLoaded = true;
            }
        }
    }

    // ── Custom emoji loading ─────────────────────────────────────────────

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
        for (Map.Entry<String, String> entry : twemojiManager.standardEmojis.entrySet()) {
            newTrie.insert(entry.getKey(), entry.getValue());
        }
        synchronized (customEmojis) {
            for (Map.Entry<String, String> entry : customEmojis.entrySet()) {
                newTrie.insert(entry.getKey(), entry.getValue());
            }
        }
        this.activeTrie = newTrie;
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

    private void writeFontJson() throws IOException {
        Path defaultFontPath = packDir.resolve("assets/minecraft/font/default.json");
        Files.createDirectories(defaultFontPath.getParent());

        FontConfig defaultFontConfig = new FontConfig();

        synchronized (customEmojis) {
            for (Map.Entry<String, String> entry : customEmojis.entrySet()) {
                String fullTrigger = entry.getKey();
                String name = fullTrigger.substring(1, fullTrigger.length() - 1);
                String safeName = safeNameCache.get(name);
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

        Files.writeString(defaultFontPath, GSON.toJson(defaultFontConfig));
    }

    // ── Image download ───────────────────────────────────────────────────

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

    // ── Unicode → PUA replacement ────────────────────────────────────────

    public String replaceUnicodeEmojisWithPua(String text) {
        Map<Integer, String> charToPua = twemojiManager.standardCharToPua;
        if (text == null || text.isEmpty() || charToPua.isEmpty()) {
            return text;
        }
        StringBuilder sb = null;
        int i = 0;
        int len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);
            String replacement = charToPua.get(cp);
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
