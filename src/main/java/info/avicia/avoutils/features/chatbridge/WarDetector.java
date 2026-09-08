package info.avicia.avoutils.features.chatbridge;

import com.wynntils.core.components.Models;
import com.wynntils.models.war.type.WarBattleInfo;
import com.wynntils.models.war.type.WarTowerState;
import com.wynntils.utils.type.RangedValue;
import info.avicia.avoutils.AvoUtilsMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detects guild war stats and outcomes using Wynntils tower state API.
 */
final class WarDetector {

    record WarResult(String outcome, String territory, String stats, String warrers,
                     long durationSeconds, long dps) {
        String formattedMessage() {
            return "**" + outcome + ": " + territory + "**\n"
                    + stats
                    + "\n⏱ " + formatDuration(durationSeconds)
                    + (dps > 0 ? " · ⚔ " + formatNumber(dps) + " dps" : "")
                    + (warrers.isEmpty() ? "" : "\n👥 " + warrers);
        }
    }

    private static final double TRACKING_RADIUS_SQ = 120.0 * 120.0;
    private static final long GRACE_PERIOD_MS = 5_000;

    private static final Pattern TERRITORY_CAPTURED = Pattern.compile("(?i)Territory\\s+Captured");
    private static final Pattern WAR_LOST = Pattern.compile("(?i)lost\\s+the\\s+war\\s+for");
    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    private static String activeBattleId;
    private static WarBattleInfo activeInfo;
    private static List<String> activeWarrers;
    private static boolean submissionSent;
    private static long warDisappearedAt;

    static void tick() {
        try {
            doTick();
        } catch (Throwable ignored) {
            // Wynntils not present; war detection unavailable
        }
    }

    private static void doTick() {
        WarBattleInfo info = Models.GuildWarTower.getWarBattleInfo().orElse(null);
        if (info != null) {
            warDisappearedAt = 0;
            String battleId = info.getTerritory() + ":" + info.getInitialState().timestamp();
            if (!battleId.equals(activeBattleId)) {
                activeBattleId = battleId;
                activeInfo = info;
                activeWarrers = collectNearbyPlayers();
                submissionSent = false;
                AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/War] Tracking war: territory='{}' warrers={}",
                        info.getTerritory(), activeWarrers);
            } else {
                activeInfo = info;
            }
        } else if (activeBattleId != null && !submissionSent) {
            // War disappeared from API; allow grace period for chat message
            if (warDisappearedAt == 0) {
                warDisappearedAt = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - warDisappearedAt > GRACE_PERIOD_MS) {
                AvoUtilsMod.LOGGER.warn("[AvoUtils] [ChatBridge/War] War disappeared without outcome chat — resetting");
                reset();
            }
        }
    }

    /**
     * Called from {@link ChatBridgeFeature#onSystemChat} for every system message.
     * Returns a structured war result if a war outcome chat line is detected,
     * but only when the local player was confirmed to be in the war via Wynntils API.
     */
    static WarResult tryDetectOutcome(String cleaned) {
        if (activeBattleId == null || submissionSent) return null;

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

        WarTowerState initial = activeInfo.getInitialState();
        RangedValue dmg = initial.damage();
        long hp = initial.health();
        double atk = initial.attackSpeed();
        double def = initial.defense();

        int dmgLow = dmg != null ? (int) dmg.low() : 0;
        int dmgHigh = dmg != null ? (int) dmg.high() : 0;
        
        String territory = activeInfo.getTerritory();
        long durationSeconds = activeInfo.getTotalLengthSeconds();
        long dps = activeInfo.getDps(Long.MAX_VALUE);

        List<String> warrers = sanitizeWarrers(activeWarrers);
        if (warrers.isEmpty()) warrers = sanitizeWarrers(collectNearbyPlayers());
        if (warrers.isEmpty()) {
            String local = localUsername();
            if (isValidUsername(local)) warrers = List.of(local);
        }

        AvoUtilsMod.LOGGER.info(
                "[AvoUtils] [ChatBridge/War] {}: territory='{}' hp={} def={}% dmg={}-{} atk={}x duration={}s dps={} warrers={}",
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

    static void reset() {
        activeBattleId = null;
        activeInfo = null;
        activeWarrers = null;
        submissionSent = false;
        warDisappearedAt = 0;
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
        if (mc.player == null || mc.world == null) return List.of();
        Set<String> names = new LinkedHashSet<>();
        String local = localUsername();
        if (isValidUsername(local)) names.add(local.trim());
        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == null || other == mc.player) continue;
            if (mc.player.squaredDistanceTo(other) > TRACKING_RADIUS_SQ) continue;
            String name = other.getName().getString();
            if (isValidUsername(name)) names.add(name.trim());
        }
        return List.copyOf(names);
    }

    private static String localUsername() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        return mc.player.getName().getString();
    }

    private static boolean isValidUsername(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        return !trimmed.isEmpty() && VALID_USERNAME.matcher(trimmed).matches();
    }

    private static List<String> sanitizeWarrers(List<String> warrers) {
        if (warrers == null || warrers.isEmpty()) return List.of();
        Set<String> unique = new LinkedHashSet<>();
        for (String warrer : warrers) {
            if (isValidUsername(warrer)) unique.add(warrer.trim());
        }
        return List.copyOf(unique);
    }
}
