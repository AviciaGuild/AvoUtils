package info.avicia.avoutils.features.chatbridge;

import info.avicia.avoutils.AvoUtilsMod;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Detects raid completions from system chat and returns structured data
 * for both Discord relay and storage delta tracking.
 */
final class RaidDetector {

    private static final Pattern RAID_FINISH_PATTERN = Pattern.compile(
            "^(.+?) finished ([\\w ']+?) "
            + "and claimed\\s+(?:(.*?)(?:,\\s*)?(?:and\\s+)?)?"
            + "\\+(\\d+)m Guild Experience(?:, and \\+(\\d+) Seasonal Rating)?$");

    private static final Pattern RAID_REWARD_PATTERN =
            Pattern.compile("(?i)(?:(\\d+)x|no) (Emeralds?|Aspects?)");

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]{3,16}");
    private static final Pattern COMMA_SPACING_PATTERN = Pattern.compile("\\s*,\\s*");

    record RaidResult(String formattedMessage, int emeralds, int aspects) {}

    static RaidResult tryDetect(String cleaned, Text message) {
        if (!isRaidCandidateText(cleaned)) return null;
        cleaned = cleaned.replace(",and ", ", and ");

        Matcher matcher = RAID_FINISH_PATTERN.matcher(cleaned);
        if (!matcher.matches()) return null;

        String namesPart = matcher.group(1);
        if (namesPart.contains(":")) return null;

        List<String> displayedNames = parseDisplayedNames(namesPart);
        if (displayedNames.size() > 4) {
            AvoUtilsMod.LOGGER.warn("[ChatBridge/Raid] Too many displayed names, dropping: {}", namesPart);
            return null;
        }

        List<String> partyMembers = resolvePartyMembers(displayedNames, message);
        if (partyMembers.isEmpty()) {
            AvoUtilsMod.LOGGER.warn("[ChatBridge/Raid] No valid usernames found");
            return null;
        }

        String raidName = matcher.group(2);
        int aspects = 0, emeralds = 0;
        String rewardClause = matcher.group(3);
        if (rewardClause != null && !rewardClause.isBlank()) {
            Matcher rewardMatcher = RAID_REWARD_PATTERN.matcher(rewardClause);
            while (rewardMatcher.find()) {
                int amount = rewardMatcher.group(1) != null ? Integer.parseInt(rewardMatcher.group(1)) : 0;
                if (rewardMatcher.group(2).toLowerCase().startsWith("aspect")) aspects += amount;
                else emeralds += amount;
            }
        }

        double guildExp = Double.parseDouble(matcher.group(4)) / 1000.0;
        int sr = matcher.group(5) != null ? Integer.parseInt(matcher.group(5)) : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("**").append(formatPlayerList(partyMembers)).append("** finished **").append(raidName).append("**");

        List<String> parts = new ArrayList<>();
        if (aspects > 0) parts.add(aspects + "x Aspects");
        if (emeralds > 0) parts.add(emeralds + "x Emeralds");
        parts.add(String.format("+%.3fm Guild XP", guildExp));
        if (sr > 0) parts.add("+" + sr + " SR");
        if (!parts.isEmpty()) sb.append(" — ").append(String.join(", ", parts));

        AvoUtilsMod.LOGGER.info("[ChatBridge/Raid] Detected: raid='{}'", raidName);
        return new RaidResult(sb.toString(), emeralds, aspects);
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

    private static List<String> resolvePartyMembers(List<String> displayedNames, Text message) {
        Set<String> resolved = new LinkedHashSet<>();
        for (String displayedName : displayedNames) {
            if (displayedName.isBlank()) continue;

            String real = UsernameResolver.resolve(message, displayedName);
            if (real != null) {
                resolved.add(real);
            } else if (USERNAME_PATTERN.matcher(displayedName).matches()) {
                resolved.add(displayedName);
            }
        }
        return List.copyOf(new ArrayList<>(resolved));
    }

    private static String formatPlayerList(List<String> members) {
        if (members.size() == 1) return members.get(0);
        if (members.size() == 2) return members.get(0) + " and " + members.get(1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) sb.append(i == members.size() - 1 ? ", and " : ", ");
            sb.append(members.get(i));
        }
        return sb.toString();
    }
}
