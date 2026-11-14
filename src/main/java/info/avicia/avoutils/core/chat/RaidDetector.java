package info.avicia.avoutils.core.chat;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.UsernameResolver;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;

/**
 * Detects raid completions from system chat and returns structured data
 * for both gateway relay and storage delta tracking.
 */
public final class RaidDetector {

    private static final Pattern RAID_FINISH_PATTERN = Pattern.compile(
            "^(.+?) finished ([\\w ']+?) "
            + "and claimed\\s+(?:(.*?)(?:,\\s*)?(?:and\\s+)?)?"
            + "\\+(\\d+)m Guild Experience(?:, and \\+(\\d+) Seasonal Rating)?$");

    private static final Pattern RAID_REWARD_PATTERN =
            Pattern.compile("(?i)(?:(\\d+)x|no) (Emeralds?|Aspects?)");

    private static final Pattern COMMA_SPACING_PATTERN = Pattern.compile("\\s*,\\s*");

    private RaidDetector() {
    }

    public record RaidResult(
            List<String> players,
            String raidName,
            int emeralds,
            int aspects,
            double guildXp,
            int seasonalRating) {
        public RaidResult {
            players = players == null ? List.of() : List.copyOf(players);
        }

        public String dedupKey() {
            return String.join(",", players)
                    + "\u0000" + raidName
                    + "\u0000" + aspects
                    + "\u0000" + emeralds
                    + "\u0000" + guildXp
                    + "\u0000" + seasonalRating;
        }
    }

    public static RaidResult tryDetect(String cleaned, Component message) {
        if (cleaned == null || !isRaidCandidateText(cleaned)) return null;
        cleaned = cleaned.replace(",and ", ", and ");

        Matcher matcher = RAID_FINISH_PATTERN.matcher(cleaned);
        if (!matcher.matches()) return null;

        String namesPart = matcher.group(1);
        if (namesPart.contains(":")) return null;

        List<String> displayedNames = parseDisplayedNames(namesPart);
        if (displayedNames.size() > 4) {
            AvoUtilsMod.LOGGER.warn("[AvoUtils] [RaidDetector] Too many displayed names, dropping: {}", namesPart);
            return null;
        }

        List<String> partyMembers = resolvePartyMembers(displayedNames, message);
        if (partyMembers.isEmpty()) {
            AvoUtilsMod.LOGGER.warn("[AvoUtils] [RaidDetector] No valid usernames found");
            return null;
        }

        String raidName = matcher.group(2).trim();
        int aspects = 0;
        int emeralds = 0;
        double guildXp;
        int sr;
        try {
            String rewardClause = matcher.group(3);
            if (rewardClause != null && !rewardClause.isBlank()) {
                Matcher rewardMatcher = RAID_REWARD_PATTERN.matcher(rewardClause);
                while (rewardMatcher.find()) {
                    int amount = rewardMatcher.group(1) != null ? Integer.parseInt(rewardMatcher.group(1)) : 0;
                    if (rewardMatcher.group(2).regionMatches(true, 0, "aspect", 0, 6)) aspects += amount;
                    else emeralds += amount;
                }
            }
            guildXp = Double.parseDouble(matcher.group(4)) / 1000.0;
            sr = matcher.group(5) != null ? Integer.parseInt(matcher.group(5)) : 0;
        } catch (NumberFormatException e) {
            return null;
        }

        return new RaidResult(partyMembers, raidName, emeralds, aspects, guildXp, sr);
    }

    private static boolean isRaidCandidateText(String cleaned) {
        return cleaned.contains("finished") && cleaned.contains("claimed")
                && cleaned.contains("Guild") && cleaned.contains("Experience");
    }

    private static List<String> parseDisplayedNames(String namesPart) {
        String canonical = namesPart.replace(", and ", ", ").replace(" and ", ", ").trim();
        return COMMA_SPACING_PATTERN.splitAsStream(canonical)
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private static List<String> resolvePartyMembers(List<String> displayedNames, Component message) {
        Set<String> resolved = new LinkedHashSet<>();
        for (String displayedName : displayedNames) {
            if (displayedName.isBlank()) continue;
            String real = UsernameResolver.resolve(message, displayedName);
            if (real != null) {
                resolved.add(real);
            }
        }
        return List.copyOf(resolved);
    }
}
