package info.avicia.avoutils.features.chatbridge;

import info.avicia.avoutils.AvoUtilsMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects guild war outcomes (win/loss) from system chat by tracking
 * Tower Stats blocks and completion messages.
 */
final class WarDetector {

    // Tower Stats block patterns
    private static final Pattern TOWER_STATS_INITIAL = Pattern.compile(
            "(?i)Tower Stats - Initial");
    private static final Pattern TERRITORY_GUILD = Pattern.compile(
            "([\\w ']+?)\\s*\\[([^\\]]+)\\]");
    private static final Pattern STAT_DAMAGE = Pattern.compile(
            "✣ Damage:\\s*(\\d+)\\s*-\\s*(\\d+)");
    private static final Pattern STAT_ATTACK_SPEED = Pattern.compile(
            "➡ Attack Speed:\\s*([\\d.]+)x");
    private static final Pattern STAT_HEALTH = Pattern.compile(
            "❤ Health:\\s*(\\d+)");
    private static final Pattern STAT_DEFENSE = Pattern.compile(
            "⛨ Defense:\\s*([\\d.]+)%");
    private static final Pattern STAT_TIME = Pattern.compile(
            "🕑 Time in War:\\s*(\\d+)s");

    // Completion message patterns
    private static final Pattern WAR_WIN = Pattern.compile(
            "You have taken control of ([\\w ']+) from \\[([^\\]]+)\\]!");
    private static final Pattern WAR_LOSS = Pattern.compile(
            "Your guild has lost the war for ([\\w ']+)\\.");

    private static final double TRACKING_RADIUS_SQ = 120.0 * 120.0;
    private static final long PENDING_TIMEOUT_MS = 30_000;

    private static PendingWar pendingWar;
    private static long pendingWarTime;

    static String tryDetect(String cleaned) {
        if (cleaned.isEmpty()) return null;

        // Check for Tower Stats
        if (TOWER_STATS_INITIAL.matcher(cleaned).find()) {
            pendingWar = parseTowerStats(cleaned);
            pendingWarTime = System.currentTimeMillis();
            return null; // don't send yet, wait for completion
        }

        // Check for completion messages
        String result = tryComplete(cleaned);
        if (result != null) {
            pendingWar = null;
            return result;
        }

        // Expire stale pending context
        if (pendingWar != null
                && System.currentTimeMillis() - pendingWarTime > PENDING_TIMEOUT_MS) {
            pendingWar = null;
        }

        return null;
    }

    // Tower Stats parsing

    private static PendingWar parseTowerStats(String cleaned) {
        // Split on the equals separators to find individual stat lines
        // The block looks like: "...Initial...===... Territory [Guild] stat1 stat2 ...==="
        String territory = null;
        String enemyGuild = null;
        Integer damageLow = null, damageHigh = null;
        Double attackSpeed = null;
        Integer health = null;
        Double defense = null;
        Integer timeSec = null;

        Matcher tgMatcher = TERRITORY_GUILD.matcher(cleaned);
        Matcher damageMatcher = STAT_DAMAGE.matcher(cleaned);
        Matcher atkSpdMatcher = STAT_ATTACK_SPEED.matcher(cleaned);
        Matcher healthMatcher = STAT_HEALTH.matcher(cleaned);
        Matcher defMatcher = STAT_DEFENSE.matcher(cleaned);
        Matcher timeMatcher = STAT_TIME.matcher(cleaned);

        if (damageMatcher.find()) {
            damageLow = Integer.parseInt(damageMatcher.group(1));
            damageHigh = Integer.parseInt(damageMatcher.group(2));
        }
        if (atkSpdMatcher.find()) {
            attackSpeed = Double.parseDouble(atkSpdMatcher.group(1));
        }
        if (healthMatcher.find()) {
            health = Integer.parseInt(healthMatcher.group(1));
        }
        if (defMatcher.find()) {
            defense = Double.parseDouble(defMatcher.group(1));
        }
        if (timeMatcher.find()) {
            timeSec = Integer.parseInt(timeMatcher.group(1));
        }

        // Territory+enemy: find "Name [Guild]" pattern
        // The territory name comes before the stats in the normalized text
        // Look for a word sequence followed by [enemy]
        if (tgMatcher.find()) {
            territory = tgMatcher.group(1).trim();
            enemyGuild = tgMatcher.group(2).trim();
        }

        if (territory == null) return null;

        AvoUtilsMod.LOGGER.info(
                "[ChatBridge/War] Tower stats parsed: territory='{}' enemy='{}' dmg={}-{} atk={}x hp={} def={}% time={}s",
                territory, enemyGuild, damageLow, damageHigh, attackSpeed, health, defense, timeSec);

        return new PendingWar(territory, enemyGuild,
                damageLow, damageHigh, attackSpeed, health, defense, timeSec);
    }

    // Completion detection

    private static String tryComplete(String cleaned) {
        if (pendingWar == null) return null;

        Matcher winMatcher = WAR_WIN.matcher(cleaned);
        if (winMatcher.find()) {
            String territory = winMatcher.group(1).trim();
            String enemyGuild = winMatcher.group(2).trim();
            if (!territory.equalsIgnoreCase(pendingWar.territory)) return null;

            return formatResult("Captured", pendingWar, enemyGuild);
        }

        Matcher lossMatcher = WAR_LOSS.matcher(cleaned);
        if (lossMatcher.find()) {
            String territory = lossMatcher.group(1).trim();
            if (!territory.equalsIgnoreCase(pendingWar.territory)) return null;

            return formatResult("Failed", pendingWar, pendingWar.enemyGuild);
        }

        return null;
    }

    // Formatting

    private static String formatResult(String outcome, PendingWar war, String enemyGuild) {
        List<String> warrers = collectNearbyPlayers();
        if (warrers.isEmpty()) {
            String localName = getLocalPlayerName();
            if (UsernameResolver.isValid(localName)) warrers = List.of(localName);
        }

        AvoUtilsMod.LOGGER.info(
                "[ChatBridge/War] {}: territory='{}' enemy='{}' warrers={}",
                outcome, war.territory, enemyGuild, warrers);

        StringBuilder sb = new StringBuilder();
        sb.append("**").append(outcome).append(": ").append(war.territory).append("**");

        if (enemyGuild != null) {
            sb.append(" vs [").append(enemyGuild).append("]");
        }

        if (war.health != null) {
            sb.append("\n").append(formatStats(war));
        }

        if (!warrers.isEmpty()) {
            sb.append("\n👥 ").append(String.join(", ", warrers));
        }

        return sb.toString();
    }

    private static String formatStats(PendingWar war) {
        StringBuilder sb = new StringBuilder();
        if (war.health != null) {
            sb.append("❤ ").append(formatNumber(war.health));
            if (war.defense != null) sb.append(" (").append(war.defense).append("%)");
        }
        if (war.damageLow != null && war.damageHigh != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("☠ ").append(formatNumber(war.damageLow))
                    .append("-").append(formatNumber(war.damageHigh));
            if (war.attackSpeed != null) sb.append(" (").append(war.attackSpeed).append("x)");
        }
        return sb.toString();
    }

    private static String formatNumber(int value) {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format("%.1fk", value / 1_000.0);
        return String.valueOf(value);
    }

    // Player collection

    private static List<String> collectNearbyPlayers() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return List.of();

        Set<String> uniqueNames = new LinkedHashSet<>();
        String localName = getLocalPlayerName();
        if (UsernameResolver.isValid(localName)) uniqueNames.add(localName.trim());

        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == null || other == mc.player) continue;
            if (mc.player.squaredDistanceTo(other) > TRACKING_RADIUS_SQ) continue;
            String name = other.getName().getString();
            if (UsernameResolver.isValid(name)) uniqueNames.add(name.trim());
        }

        return uniqueNames.isEmpty() ? List.of() : List.copyOf(uniqueNames);
    }

    private static String getLocalPlayerName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        return mc.player.getName().getString();
    }

    // Pending context

    private record PendingWar(
            String territory,
            String enemyGuild,
            Integer damageLow,
            Integer damageHigh,
            Double attackSpeed,
            Integer health,
            Double defense,
            Integer timeSec) {
    }
}
