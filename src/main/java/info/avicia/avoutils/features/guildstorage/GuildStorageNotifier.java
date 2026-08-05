package info.avicia.avoutils.features.guildstorage;

import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.util.PacketTextNormalizer;
import info.avicia.avoutils.core.util.WynnPillUtil;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks guild emerald/aspect storage and notifies on configurable threshold
 * crossings. Receives data from three sources:
 *   Menu scanning: absolute counts from guild manage menu lore
 *   Chat deltas: raid rewards (+emeralds/+aspects) and reward grants (-emeralds/-aspects)
 *   Remote snapshots: crowdsourced via AvoBot WebSocket relay
 */
public class GuildStorageNotifier implements AvoFeature {

    private static final String EVT_GUILD_STORAGE = "guild_storage_snapshot";
    private static final long PUBLISH_INTERVAL_MS = 1_000L;
    private static final long LOCAL_AUTHORITY_WINDOW_MS = 5_000L;

    // ── Menu lore patterns ──────────────────────────────────────────────
    private static final Pattern EMERALDS_PATTERN =
            Pattern.compile("(?i)^emeralds:\\s*([\\d, ]+)\\s*/\\s*([\\d, ]+)$");
    private static final Pattern ASPECTS_PATTERN =
            Pattern.compile("(?i)^aspects:\\s*([\\d, ]+)\\s*/\\s*([\\d, ]+)$");
    private static final String REWARDS_UNAVAILABLE = "rewards are unavailable";

    private ModConfig config;

    // ── Tracked state ───────────────────────────────────────────────────
    private long emeraldCurrent = -1;
    private long emeraldMax = -1;
    private long aspectCurrent = -1;
    private long aspectMax = -1;

    private boolean emeraldNotified;
    private boolean aspectNotified;

    // ── Publish debounce ────────────────────────────────────────────────
    private long lastPublishedEmeraldCurrent = -1;
    private long lastPublishedEmeraldMax = -1;
    private long lastPublishedAspectCurrent = -1;
    private long lastPublishedAspectMax = -1;
    private long lastPublishTimeMs;
    private long lastObservedLocallyAtMs;

    public boolean isGuildMember() {
        Boolean cached = AvoAuthService.getInstance().getCachedGuildMember();
        return cached != null && cached;
    }

    private boolean isActive() {
        return config != null && isGuildMember();
    }

    private boolean areNotifsEnabled() {
        return config != null && config.guildStorageNotifsEnabled;
    }

    @Override
    public void initialize(ModConfig config) {
        this.config = config;

        AvoWebSocketManager.getInstance().registerConnectionDemand("guildstorage", this::isActive);

        AvoWebSocketManager.getInstance().registerListener(EVT_GUILD_STORAGE, json -> {
            if (!isActive()) return;
            try {
                applyRemoteSnapshot(
                        json.get("emerald_current").getAsLong(),
                        json.get("emerald_max").getAsLong(),
                        json.get("aspect_current").getAsLong(),
                        json.get("aspect_max").getAsLong());
            } catch (Exception e) {
                AvoUtilsMod.LOGGER.warn("[GuildStorageNotifier] Malformed remote snapshot", e);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());

        AvoUtilsMod.LOGGER.info("[GuildStorageNotifier] Initialized. Emerald threshold: {}%, Aspect threshold: {}%",
                config.guildStorageEmeraldThresholdPercent, config.guildStorageAspectThresholdPercent);
    }

    // ── Menu scanning (called from HandledScreenMixin) ──────────────────

    public void onContainerRender(ScreenHandler handler) {
        if (!isActive() || handler == null) return;
        if (MinecraftClient.getInstance().player == null) return;

        StorageSnapshot snapshot = extractSnapshot(handler);
        if (snapshot == null) return;

        lastObservedLocallyAtMs = System.currentTimeMillis();
        applyAbsoluteState(snapshot.emeraldCurrent, snapshot.emeraldMax,
                snapshot.aspectCurrent, snapshot.aspectMax);
    }

    // ── Chat delta consumers (called from ChatBridgeFeature) ────────────

    public void onRaidDelta(int emeralds, int aspects) {
        if (!isActive() || !isSeeded()) return;
        applyDelta(emeralds, aspects);
    }

    public void onRewardDelta(long emeraldDelta, long aspectDelta) {
        if (!isActive() || !isSeeded()) return;
        applyDelta(emeraldDelta, aspectDelta);
    }

    // ── State mutation ──────────────────────────────────────────────────

    private void applyAbsoluteState(long emCur, long emMax, long asCur, long asMax) {
        boolean changed = emCur != emeraldCurrent || emMax != emeraldMax
                || asCur != aspectCurrent || asMax != aspectMax;

        if (changed) {
            checkAndNotify(emCur, emMax, asCur, asMax);
            emeraldCurrent = emCur;
            emeraldMax = emMax;
            aspectCurrent = asCur;
            aspectMax = asMax;
            resetFlagsIfBelow();
        }
        publishIfChanged();
    }

    private void applyDelta(long emeraldDelta, long aspectDelta) {
        if (emeraldCurrent < 0) return;

        long newEmCur = Math.max(0, emeraldCurrent + emeraldDelta);
        long newAsCur = Math.max(0, aspectCurrent + aspectDelta);

        checkAndNotify(newEmCur, emeraldMax, newAsCur, aspectMax);
        emeraldCurrent = newEmCur;
        aspectCurrent = newAsCur;
        resetFlagsIfBelow();
        publishIfChanged();
    }

    private void applyRemoteSnapshot(long emCur, long emMax, long asCur, long asMax) {
        if (!isActive()) return;

        long ageMs = System.currentTimeMillis() - lastObservedLocallyAtMs;
        if (ageMs >= 0 && ageMs <= LOCAL_AUTHORITY_WINDOW_MS) return;

        applyAbsoluteState(emCur, emMax, asCur, asMax);
        AvoUtilsMod.LOGGER.debug("[GuildStorageNotifier] Applied remote snapshot emerald={}/{} aspect={}/{}",
                emCur, emMax, asCur, asMax);
    }

    private boolean isSeeded() {
        return emeraldCurrent >= 0 && emeraldMax > 0 && aspectCurrent >= 0 && aspectMax > 0;
    }

    // ── Threshold checks ────────────────────────────────────────────────

    private void checkAndNotify(long emCur, long emMax, long asCur, long asMax) {
        if (emeraldCurrent >= 0 && emMax > 0) {
            double prevFrac = emeraldMax > 0 ? (double) emeraldCurrent / emeraldMax : 0;
            double currFrac = (double) emCur / emMax;
            double thresh = config.guildStorageEmeraldThresholdPercent / 100.0;
            if (prevFrac < thresh && currFrac >= thresh && !emeraldNotified) {
                notifyThreshold("Emeralds", emCur, emMax, config.guildStorageEmeraldThresholdPercent);
                emeraldNotified = true;
            }
        }
        if (aspectCurrent >= 0 && asMax > 0) {
            double prevFrac = aspectMax > 0 ? (double) aspectCurrent / aspectMax : 0;
            double currFrac = (double) asCur / asMax;
            double thresh = config.guildStorageAspectThresholdPercent / 100.0;
            if (prevFrac < thresh && currFrac >= thresh && !aspectNotified) {
                notifyThreshold("Aspects", asCur, asMax, config.guildStorageAspectThresholdPercent);
                aspectNotified = true;
            }
        }
    }

    private void resetFlagsIfBelow() {
        double emThresh = config.guildStorageEmeraldThresholdPercent / 100.0;
        double emFrac = emeraldMax > 0 ? (double) emeraldCurrent / emeraldMax : 0;
        if (emFrac < emThresh) emeraldNotified = false;

        double asThresh = config.guildStorageAspectThresholdPercent / 100.0;
        double asFrac = aspectMax > 0 ? (double) aspectCurrent / aspectMax : 0;
        if (asFrac < asThresh) aspectNotified = false;
    }

    // ── Publishing ──────────────────────────────────────────────────────

    private void publishIfChanged() {
        boolean changed = emeraldCurrent != lastPublishedEmeraldCurrent
                || emeraldMax != lastPublishedEmeraldMax
                || aspectCurrent != lastPublishedAspectCurrent
                || aspectMax != lastPublishedAspectMax;
        if (!changed) return;

        long now = System.currentTimeMillis();
        if (now - lastPublishTimeMs < PUBLISH_INTERVAL_MS) return;

        JsonObject payload = new JsonObject();
        payload.addProperty("emerald_current", emeraldCurrent);
        payload.addProperty("emerald_max", emeraldMax);
        payload.addProperty("aspect_current", aspectCurrent);
        payload.addProperty("aspect_max", aspectMax);
        AvoWebSocketManager.getInstance().sendEvent(EVT_GUILD_STORAGE, payload);

        lastPublishedEmeraldCurrent = emeraldCurrent;
        lastPublishedEmeraldMax = emeraldMax;
        lastPublishedAspectCurrent = aspectCurrent;
        lastPublishedAspectMax = aspectMax;
        lastPublishTimeMs = now;
    }

    // ── Notification ────────────────────────────────────────────────────

    private void notifyThreshold(String resourceName, long current, long max, int thresholdPercent) {
        if (!areNotifsEnabled()) return;
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                var msg = WynnPillUtil.createPrefixedPill("AvoUtils", false)
                        .append(Text.literal(resourceName).formatted(Formatting.GOLD))
                        .append(Text.literal(" storage reached ").formatted(Formatting.GRAY))
                        .append(Text.literal(thresholdPercent + "%").formatted(Formatting.GREEN))
                        .append(Text.literal("!").formatted(Formatting.GRAY));
                MinecraftClient.getInstance().player.sendMessage(msg, false);
            }
        });
    }

    public void toggleStorage() {
        if (!isGuildMember()) {
            MinecraftClient.getInstance().execute(() -> {
                if (MinecraftClient.getInstance().player != null) {
                    var msg = WynnPillUtil.createPrefixedPill("AvoUtils", true)
                            .append(Text.literal("Storage notifications are unavailable: you are not in Avicia.")
                                    .formatted(Formatting.RED));
                    MinecraftClient.getInstance().player.sendMessage(msg, false);
                }
            });
            return;
        }

        config.guildStorageNotifsEnabled = !config.guildStorageNotifsEnabled;
        config.save();
        Formatting statusColor = config.guildStorageNotifsEnabled ? Formatting.GREEN : Formatting.RED;
        String statusWord = config.guildStorageNotifsEnabled ? "enabled" : "disabled";
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                var msg = WynnPillUtil.createPrefixedPill("AvoUtils", false)
                        .append(Text.literal("Storage threshold notifications are now ").formatted(Formatting.GRAY))
                        .append(Text.literal(statusWord).formatted(statusColor))
                        .append(Text.literal(".").formatted(Formatting.GRAY));
                MinecraftClient.getInstance().player.sendMessage(msg, false);
            }
        });
    }

    public void reset() {
        emeraldCurrent = -1; emeraldMax = -1;
        aspectCurrent = -1; aspectMax = -1;
        emeraldNotified = false; aspectNotified = false;
        lastPublishedEmeraldCurrent = -1; lastPublishedEmeraldMax = -1;
        lastPublishedAspectCurrent = -1; lastPublishedAspectMax = -1;
        lastPublishTimeMs = 0; lastObservedLocallyAtMs = 0;
    }

    // ── Menu snapshot extraction ────────────────────────────────────────

    static StorageSnapshot extractSnapshot(ScreenHandler handler) {
        for (Slot slot : handler.slots) {
            if (slot == null || !slot.hasStack()) continue;
            StorageSnapshot snapshot = parseSnapshot(slot.getStack());
            if (snapshot != null) return snapshot;
        }
        return null;
    }

    static StorageSnapshot parseSnapshot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null || lore.lines().isEmpty()) return null;
        return parseSnapshotLines(lore.lines().stream().map(Text::getString).toList());
    }

    static StorageSnapshot parseSnapshotLines(List<String> rawLines) {
        if (rawLines == null || rawLines.isEmpty()) return null;
        long emCur = -1, emMax = -1, asCur = -1, asMax = -1;
        for (String rawLine : rawLines) {
            String line = PacketTextNormalizer.normalizeForParsing(rawLine);
            if (line.isEmpty()) continue;
            if (line.toLowerCase().contains(REWARDS_UNAVAILABLE)) return null;
            Matcher em = EMERALDS_PATTERN.matcher(line);
            if (em.matches()) { emCur = parseNumber(em.group(1)); emMax = parseNumber(em.group(2)); continue; }
            Matcher as = ASPECTS_PATTERN.matcher(line);
            if (as.matches()) { asCur = parseNumber(as.group(1)); asMax = parseNumber(as.group(2)); }
        }
        if (emCur < 0 || emMax <= 0 || asCur < 0 || asMax <= 0) return null;
        return new StorageSnapshot(emCur, emMax, asCur, asMax);
    }

    private static long parseNumber(String raw) {
        return Long.parseLong(raw.replace(",", "").replace(" ", "").trim());
    }

    record StorageSnapshot(long emeraldCurrent, long emeraldMax, long aspectCurrent, long aspectMax) {}
}
