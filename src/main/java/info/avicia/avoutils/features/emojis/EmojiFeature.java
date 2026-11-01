package info.avicia.avoutils.features.emojis;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.command.AvoCommands;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.util.WynnPillUtil;
import info.avicia.avoutils.features.emojis.EmojiDownloader.AnimationCacheEntry;
import info.avicia.avoutils.features.emojis.EmojiDownloader.EmojiManifest;
import info.avicia.avoutils.features.emojis.animation.AnimationMeta;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class EmojiFeature implements AvoFeature {
    private final TwemojiManager twemojiManager;
    private final EmojiPackWriter packWriter;
    private final EmojiDownloader downloader;

    private final Map<String, String> customEmojis = new ConcurrentHashMap<>();
    private final Map<Integer, AnimationMeta> animatedEmojis = new ConcurrentHashMap<>();
    private ModConfig config;

    private final AtomicBoolean loadingEmojis = new AtomicBoolean(false);

    private volatile EmojiTrie activeTrie = new EmojiTrie();
    private volatile boolean packsLoaded = false;

    public EmojiFeature() {
        this(EmojiPackWriter.resolvePackFormat());
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
        this.packWriter = new EmojiPackWriter(packDir, packFormat);
        this.downloader = new EmojiDownloader(packDir);
    }

    void setConfig(ModConfig config) {
        this.config = config;
    }

    Map<String, String> getCustomEmojis() {
        return customEmojis;
    }

    Map<String, AnimationCacheEntry> getAnimationCache() {
        return downloader.animationCache();
    }

    boolean writeFontJsonForTesting() throws IOException {
        return packWriter.writeFontJson(customEmojis, animatedEmojis, twemojiManager);
    }

    Path getManifestPath() {
        return downloader.manifestPath();
    }

    EmojiManifest loadManifestForTesting() {
        return downloader.loadManifest();
    }

    void saveManifestForTesting(EmojiManifest manifest) {
        downloader.saveManifest(manifest);
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

    public List<String> getMatchingShortcodes(String prefix) {
        synchronized (customEmojis) {
            return EmojiAutocomplete.matchShortcodes(customEmojis.keySet(), twemojiManager.standardEmojis.keySet(), prefix);
        }
    }

    public String getEmojiReplacement(String shortcode) {
        if (shortcode == null)
            return null;
        String custom = customEmojis.get(shortcode);
        if (custom != null)
            return custom;
        String twemoji = twemojiManager.standardEmojis.get(shortcode);
        if (twemoji != null)
            return twemoji;
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
            packWriter.createResourcePackStructure();
        } catch (IOException e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] Failed to create emoji resource pack folders", e);
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

    @Override
    public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        // /avo emojis reload / update → refresh and re-download emojis
        Command<FabricClientCommandSource> reloadEmojisCommand = context -> {
            if (AvoCommands.ensureOnWynncraft(context.getSource())) {
                handleReloadCommand();
            }
            return 1;
        };
        root.then(literal("emojis")
                .then(literal("reload")
                        .executes(reloadEmojisCommand))
                .then(literal("update")
                        .executes(reloadEmojisCommand)));
    }

    public CompletableFuture<Void> reloadEmojis() {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.deleteIfExists(downloader.manifestPath());
            } catch (Exception ignored) {
            }
            twemojiManager.loadResources();
            loadAndCacheEmojis();
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                reloadResourcesWithPacks(client);
            }
        });
    }

    public void handleReloadCommand() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) {
            client.player.displayClientMessage(
                    WynnPillUtil.createPrefixedPill("AvoUtils", false)
                            .append(Component.literal("Checking and updating emojis...").withStyle(ChatFormatting.GRAY)),
                    false
            );
        }
        reloadEmojis().thenRun(() -> {
            Minecraft c = Minecraft.getInstance();
            if (c != null) {
                c.execute(() -> {
                    if (c.player != null) {
                        c.player.displayClientMessage(
                                WynnPillUtil.createPrefixedPill("AvoUtils", false)
                                        .append(Component.literal("Emojis updated successfully!").withStyle(ChatFormatting.GREEN)),
                                false
                        );
                    }
                });
            }
        }).exceptionally(ex -> {
            Minecraft c = Minecraft.getInstance();
            if (c != null) {
                c.execute(() -> {
                    if (c.player != null) {
                        String err = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        c.player.displayClientMessage(
                                WynnPillUtil.createPrefixedPill("AvoUtils", true)
                                        .append(Component.literal("Failed to update emojis: " + err).withStyle(ChatFormatting.RED)),
                                false
                        );
                    }
                });
            }
            return null;
        });
    }

    public static void reloadResourcesWithPacks(Minecraft client) {
        if (client == null)
            return;
        client.execute(() -> {
            if (client.getResourcePackRepository() == null)
                return;
            client.getResourcePackRepository().reload();
            List<String> enabled = new ArrayList<>(client.getResourcePackRepository().getSelectedIds());
            if (client.getResourcePackRepository().isAvailable("avoutils/twemoji") && !enabled.contains("avoutils/twemoji")) {
                enabled.add("avoutils/twemoji");
            }
            if (client.getResourcePackRepository().isAvailable("avoutils/emojis") && !enabled.contains("avoutils/emojis")) {
                enabled.add("avoutils/emojis");
            }
            client.getResourcePackRepository().setSelected(enabled);
            client.reloadResourcePacks();
        });
    }

    public void ensurePacksLoaded() {
        if (config != null && config.emojiEnabled && !packsLoaded) {
            Minecraft client = Minecraft.getInstance();
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
            AvoUtilsMod.LOGGER.info("[AvoUtils] Starting loading and caching emojis...");
            EmojiDownloader.LoadResult result = downloader.loadAndCache();
            if (result != null) {
                if (result.interrupted()) {
                    return false;
                }
                changed = result.changed();
                useCustomEmojis(result.readyEmojis());
            }

            rebuildActiveEmojis();
            boolean fontChanged = packWriter.writeFontJson(customEmojis, animatedEmojis, twemojiManager);
            return changed || fontChanged;

        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] Error occurred while loading emojis", e);
            return false;
        } finally {
            loadingEmojis.set(false);
        }
    }

    /** Gives each ready emoji its code point (a run of them for an animated one's frames), in name order. */
    private void useCustomEmojis(List<String> sortedNames) {
        Map<String, String> newCustomEmojis = new HashMap<>();
        Map<Integer, AnimationMeta> newAnimatedEmojis = new HashMap<>();
        int currentCodePoint = 0xF0000;
        for (String name : sortedNames) {
            newCustomEmojis.put(":" + name + ":", Character.toString(currentCodePoint));
            AnimationCacheEntry anim = downloader.animationCache().get(name);
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
        AvoUtilsMod.LOGGER.info("[AvoUtils] Successfully loaded {} custom emojis ({} animated).",
                sortedNames.size(), animatedEmojis.size());
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
}
