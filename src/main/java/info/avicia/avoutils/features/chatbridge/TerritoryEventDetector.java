package info.avicia.avoutils.features.chatbridge;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.PlayerUtil;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects enemy territory captures and failed enemy attacks (successful defenses)
 * on Avicia territories from system chat.
 */
final class TerritoryEventDetector {

    static final long DEFENSE_PAIR_WINDOW_MS = 5_000L;

    enum Action {
        CAPTURE("capture"),
        DEFENSE("defense");

        private final String wireName;

        Action(String wireName) {
            this.wireName = wireName;
        }

        String wireName() {
            return wireName;
        }
    }

    private static final Pattern ENEMY_CAPTURE_PATTERN = Pattern.compile(
            "^\\[(?<tag>[A-Za-z0-9]{3,4})]\\s+has taken control of\\s+(?<territory>[^:\\[\\]]+?)[!.]*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern COMBINED_DEFENDED_FIRST_PATTERN = Pattern.compile(
            "^Your guild has successfully defended\\s+(?<territory>[^:.!\\[\\]]+?)[.!]*\\s+"
                    + "\\[?(?<tag>[A-Za-z0-9]{3,4})]?\\s+has lost the war[!.]*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern COMBINED_LOST_FIRST_PATTERN = Pattern.compile(
            "^\\[?(?<tag>[A-Za-z0-9]{3,4})]?\\s+has lost the war[!.]*\\s+"
                    + "Your guild has successfully defended\\s+(?<territory>[^:.!\\[\\]]+?)[.!]*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DEFENDED_PATTERN = Pattern.compile(
            "^Your guild has successfully defended\\s+(?<territory>[^:\\[\\]]+?)[.!]*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LOST_WAR_PATTERN = Pattern.compile(
            "^\\[?(?<tag>[A-Za-z0-9]{3,4})]?\\s+has lost the war[!.]*$",
            Pattern.CASE_INSENSITIVE);

    private final LongSupplier clock;

    private String pendingTerritory;
    private long pendingTerritoryAt;

    private String pendingAttacker;
    private long pendingAttackerAt;

    TerritoryEventDetector() {
        this(System::currentTimeMillis);
    }

    TerritoryEventDetector(LongSupplier clock) {
        this.clock = clock;
    }

    synchronized Result tryDetect(String cleaned) {
        if (cleaned == null || cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.contains(":") || cleaned.contains("->")) {
            return null;
        }

        if (PlayerUtil.containsIgnoreCase(cleaned, "has taken control of")) {
            Matcher capture = ENEMY_CAPTURE_PATTERN.matcher(cleaned);
            if (capture.matches()) {
                String tag = capture.group("tag").trim();
                String territory = capture.group("territory").trim();
                if (!territory.isEmpty() && !isOwnGuild(tag)) {
                    AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Territory] Enemy capture: [{}] -> {}", tag, territory);
                    return new Result(Action.CAPTURE, territory, tag);
                }
            }
            return null;
        }

        boolean hasDefended = PlayerUtil.containsIgnoreCase(cleaned, "successfully defended");
        boolean hasLostWar = PlayerUtil.containsIgnoreCase(cleaned, "has lost the war");
        if (!hasDefended && !hasLostWar) {
            return null;
        }

        long now = clock.getAsLong();
        expireStale(now);

        if (hasDefended && hasLostWar) {
            Matcher combined = COMBINED_DEFENDED_FIRST_PATTERN.matcher(cleaned);
            if (!combined.matches()) {
                combined = COMBINED_LOST_FIRST_PATTERN.matcher(cleaned);
            }
            if (combined.matches()) {
                String tag = combined.group("tag").trim();
                String territory = combined.group("territory").trim();
                if (!territory.isEmpty() && !isOwnGuild(tag)) {
                    reset();
                    return buildDefenseResult(territory, tag);
                }
            }
            return null;
        }

        if (hasDefended) {
            Matcher defended = DEFENDED_PATTERN.matcher(cleaned);
            if (defended.matches()) {
                String territory = defended.group("territory").trim();
                if (!territory.isEmpty()) {
                    if (pendingAttacker != null) {
                        String attacker = pendingAttacker;
                        reset();
                        return buildDefenseResult(territory, attacker);
                    }
                    pendingTerritory = territory;
                    pendingTerritoryAt = now;
                }
            }
            return null;
        }

        Matcher lost = LOST_WAR_PATTERN.matcher(cleaned);
        if (lost.matches()) {
            String tag = lost.group("tag").trim();
            if (!tag.isEmpty() && !isOwnGuild(tag)) {
                if (pendingTerritory != null) {
                    String territory = pendingTerritory;
                    reset();
                    return buildDefenseResult(territory, tag);
                }
                pendingAttacker = tag;
                pendingAttackerAt = now;
            }
        }
        return null;
    }

    synchronized void reset() {
        pendingTerritory = null;
        pendingTerritoryAt = 0L;
        pendingAttacker = null;
        pendingAttackerAt = 0L;
    }

    private void expireStale(long now) {
        if (pendingTerritory != null && (now - pendingTerritoryAt) > DEFENSE_PAIR_WINDOW_MS) {
            pendingTerritory = null;
            pendingTerritoryAt = 0L;
        }
        if (pendingAttacker != null && (now - pendingAttackerAt) > DEFENSE_PAIR_WINDOW_MS) {
            pendingAttacker = null;
            pendingAttackerAt = 0L;
        }
    }

    private static Result buildDefenseResult(String territory, String attackerTag) {
        AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Territory] Defended {} against [{}]", territory, attackerTag);
        return new Result(Action.DEFENSE, territory, attackerTag);
    }

    private static boolean isOwnGuild(String tag) {
        return "AVO".equalsIgnoreCase(tag) || "Avicia".equalsIgnoreCase(tag);
    }

    record Result(Action action, String territory, String guildTag) {
        String dedupKey() {
            return action.wireName() + "\u0000" + territory + "\u0000" + guildTag;
        }
    }
}
