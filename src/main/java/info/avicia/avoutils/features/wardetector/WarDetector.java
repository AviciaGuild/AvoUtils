package info.avicia.avoutils.features.wardetector;

import com.wynntils.core.components.Models;
import com.wynntils.models.war.type.WarBattleInfo;
import com.wynntils.models.war.type.WarTowerState;
import com.wynntils.utils.type.RangedValue;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.features.chatbridge.DiscordMarkdown;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detects guild war stats and outcomes using Wynntils tower state API.
 */
public final class WarDetector {

    public record WarResult(String outcome, String territory, String stats, String warrers,
                            long durationSeconds, long dps) {
        public String formattedMessage() {
            return "**" + outcome + ": " + territory + "**\n"
                    + stats
                    + "\n⏱ " + formatDuration(durationSeconds)
                    + (dps > 0 ? " · ⚔ " + formatNumber(dps) + " dps" : "")
                    + (warrers.isEmpty() ? "" : "\n👥 " + warrers);
        }
    }

    private static final double TRACKING_RADIUS_SQ = 120.0 * 120.0;
    private static final long GRACE_PERIOD_MS = 5_000L;
    private static final long SAMPLE_INTERVAL_MS = 500L;

    private static final Pattern TERRITORY_CAPTURED = Pattern.compile("(?i)Territory\\s+Captured");
    private static final Pattern WAR_LOST = Pattern.compile("(?i)lost\\s+the\\s+war\\s+for");
    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    private static String activeBattleId;
    private static String activeTerritory;
    private static Object activeInfo;
    private static final Set<String> activeWarrers = Collections.synchronizedSet(new LinkedHashSet<>());
    private static boolean submissionSent;
    private static long warDisappearedAt;
    private static long lastSampleAt;

    private WarDetector() {}

    public static synchronized void tick() {
        try {
            doTick();
        } catch (Throwable ignored) {
            // Wynntils not present; war detection unavailable
        }
    }

    private static void doTick() {
        long now = System.currentTimeMillis();
        WarBattleInfo info = Models.GuildWarTower.getWarBattleInfo().orElse(null);
        if (info != null) {
            warDisappearedAt = 0;
            String territory = info.getTerritory();
            WarTowerState initial = info.getInitialState();
            String battleId;
            if ((initial == null || initial.timestamp() <= 0)
                    && territory != null
                    && activeTerritory != null
                    && territory.equalsIgnoreCase(activeTerritory)
                    && activeBattleId != null) {
                battleId = activeBattleId;
            } else {
                long timestamp = (initial != null && initial.timestamp() > 0)
                        ? initial.timestamp()
                        : now;
                battleId = (territory == null ? "unknown" : territory) + ":" + timestamp;
            }

            if (!battleId.equals(activeBattleId)) {
                activeBattleId = battleId;
                activeTerritory = territory;
                activeInfo = info;
                activeWarrers.clear();
                activeWarrers.addAll(collectNearbyPlayers());
                lastSampleAt = now;
                submissionSent = false;
                AvoUtilsMod.LOGGER.info("[AvoUtils] [WarDetector] Tracking war: territory='{}' warrers={}",
                        info.getTerritory(), activeWarrers);
            } else if (!submissionSent) {
                activeInfo = info;
                if (now - lastSampleAt >= SAMPLE_INTERVAL_MS) {
                    activeWarrers.addAll(collectNearbyPlayers());
                    lastSampleAt = now;
                }
            }
        } else if (activeBattleId != null) {
            if (submissionSent) {
                reset();
            } else {
                if (warDisappearedAt == 0) {
                    warDisappearedAt = now;
                }
                if (now - warDisappearedAt > GRACE_PERIOD_MS) {
                    AvoUtilsMod.LOGGER.warn("[AvoUtils] [WarDetector] War disappeared without outcome chat; resetting");
                    reset();
                }
            }
        }
    }

    /**
     * Called from {@link WarDetectorFeature#onSystemChat} for every system message.
     * Returns a structured war result if a war outcome chat line is detected,
     * but only when the local player was confirmed to be in the war via Wynntils API.
     */
    public static synchronized WarResult tryDetectOutcome(String cleaned) {
        if (cleaned == null || activeBattleId == null || submissionSent) return null;

        if (TERRITORY_CAPTURED.matcher(cleaned).find()) {
            return formatWarResult("Captured");
        }

        if (WAR_LOST.matcher(cleaned).find()) {
            return formatWarResult("Failed");
        }

        return null;
    }

    private static WarResult formatWarResult(String outcome) {
        if (activeInfo == null) return null;
        submissionSent = true;

        WarBattleInfo battleInfo = (WarBattleInfo) activeInfo;
        WarTowerState initial = battleInfo.getInitialState();
        RangedValue dmg = initial != null ? initial.damage() : null;
        long hp = initial != null ? initial.health() : 0;
        double atk = initial != null ? initial.attackSpeed() : 0;
        double def = initial != null ? initial.defense() : 0;

        int dmgLow = dmg != null ? (int) dmg.low() : 0;
        int dmgHigh = dmg != null ? (int) dmg.high() : 0;

        String territory = battleInfo.getTerritory();
        long durationSeconds = battleInfo.getTotalLengthSeconds();
        long dps = battleInfo.getDps(Long.MAX_VALUE);

        // Merge any players currently nearby at war outcome time
        activeWarrers.addAll(collectNearbyPlayers());

        List<String> warrers = sanitizeWarrers(activeWarrers);
        if (warrers.isEmpty()) {
            String local = localUsername();
            if (isValidUsername(local)) warrers = List.of(local);
        }

        AvoUtilsMod.LOGGER.info(
                "[AvoUtils] [WarDetector] {}: territory='{}' hp={} def={}% dmg={}-{} atk={}x duration={}s dps={} warrers={}",
                outcome, territory, hp, def, dmgLow, dmgHigh, atk, durationSeconds, dps, warrers);

        StringBuilder stats = new StringBuilder();
        stats.append("❤ ").append(formatNumber(hp));
        if (def > 0) stats.append(" (").append(String.format(Locale.US, "%.0f", def)).append("%)");
        stats.append(" · ☠ ").append(formatNumber(dmgLow)).append("-").append(formatNumber(dmgHigh));
        if (atk > 0) stats.append(" (").append(atk).append("x)");

        StringBuilder warrersBuilder = new StringBuilder();
        for (String warrer : warrers) {
            if (warrersBuilder.length() > 0) warrersBuilder.append(", ");
            warrersBuilder.append(DiscordMarkdown.escapeUsername(warrer));
        }
        String warrersStr = warrersBuilder.toString();

        return new WarResult(outcome, territory, stats.toString(), warrersStr, durationSeconds, dps);
    }

    public static synchronized void reset() {
        activeBattleId = null;
        activeTerritory = null;
        activeInfo = null;
        activeWarrers.clear();
        submissionSent = false;
        warDisappearedAt = 0;
        lastSampleAt = 0;
    }

    private static String formatNumber(long value) {
        if (value >= 1_000_000) return String.format(Locale.US, "%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format(Locale.US, "%.1fk", value / 1_000.0);
        return String.valueOf(value);
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0) return "0s";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format("%dh %dm", h, m);
        if (m > 0) return String.format("%dm %ds", m, s);
        return s + "s";
    }

    private static List<String> collectNearbyPlayers() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return List.of();
        Set<String> names = new LinkedHashSet<>();
        String local = localUsername();
        if (isValidUsername(local)) names.add(local.trim());
        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == null || other == mc.player) continue;
            if (mc.player.squaredDistanceTo(other) > TRACKING_RADIUS_SQ) continue;
            String name = extractUsername(other);
            if (isValidUsername(name)) names.add(name.trim());
        }
        return List.copyOf(names);
    }

    private static String extractUsername(PlayerEntity player) {
        if (player == null) return null;
        if (player.getGameProfile() != null && player.getGameProfile().name() != null) {
            String name = player.getGameProfile().name();
            if (!name.isBlank()) return name;
        }
        return player.getName() != null ? player.getName().getString() : null;
    }

    private static String localUsername() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return null;
        if (mc.player != null) {
            String name = extractUsername(mc.player);
            if (isValidUsername(name)) return name;
        }
        return (mc.getSession() != null) ? mc.getSession().getUsername() : null;
    }

    private static boolean isValidUsername(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        return !trimmed.isEmpty() && VALID_USERNAME.matcher(trimmed).matches();
    }

    private static List<String> sanitizeWarrers(Collection<String> warrers) {
        if (warrers == null || warrers.isEmpty()) return List.of();
        Set<String> unique = new LinkedHashSet<>();
        synchronized (warrers) {
            for (String warrer : warrers) {
                if (isValidUsername(warrer)) unique.add(warrer.trim());
            }
        }
        return List.copyOf(unique);
    }

    static String getActiveBattleId() {
        return activeBattleId;
    }

    static Set<String> getActiveWarrers() {
        synchronized (activeWarrers) {
            return new LinkedHashSet<>(activeWarrers);
        }
    }

    static boolean isSubmissionSent() {
        return submissionSent;
    }
}
