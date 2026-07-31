package info.avicia.avoutils.features.chatbridge;

import info.avicia.avoutils.AvoUtilsMod;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects guild bank deposit/withdrawal messages and formats 
 * them for relay to Discord.
 */
final class BankDetector {

    private static final Pattern BANK_PATTERN = Pattern.compile(
            "^(.+?)\\s+(deposited|withdrew)\\s+(.+?)\\s+(to|from)\\s+the Guild Bank\\s+\\((.+)\\)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BANK_NO_TIER_PATTERN = Pattern.compile(
            "^(.+?)\\s+(deposited|withdrew)\\s+(.+?)\\s+(to|from)\\s+the Guild(?: Bank)?$",
            Pattern.CASE_INSENSITIVE);
    private static final String DEFAULT_ACCESS_TIER = "Unknown";

    static Result tryDetect(String cleaned, Text message) {
        if (!cleaned.contains("Guild")
                || (!cleaned.contains("deposited") && !cleaned.contains("withdrew"))) {
            return null;
        }

        Matcher matcher = BANK_PATTERN.matcher(cleaned);
        String accessTier;
        if (matcher.matches()) {
            accessTier = matcher.group(5).trim();
        } else {
            matcher = BANK_NO_TIER_PATTERN.matcher(cleaned);
            if (!matcher.matches()) return null;
            accessTier = DEFAULT_ACCESS_TIER;
        }

        String displayedPlayer = matcher.group(1).trim();
        String action = matcher.group(2).toLowerCase();
        String itemBlock = matcher.group(3).trim();
        if (displayedPlayer.isEmpty() || itemBlock.isEmpty()) return null;

        String realUsername = UsernameResolver.resolve(message, displayedPlayer);
        if (realUsername == null) {
            AvoUtilsMod.LOGGER.warn("[ChatBridge/Bank] Could not resolve username from '{}'", displayedPlayer);
            return null;
        }

        String displayName = DEFAULT_ACCESS_TIER.equals(accessTier)
                ? "Guild Bank"
                : "Guild Bank (" + accessTier + ")";

        String formattedMessage = "**" + realUsername + "** " + action + " **" + itemBlock + "**";

        AvoUtilsMod.LOGGER.info("[ChatBridge/Bank] Detected: {} {} {}", realUsername, action, itemBlock);

        return new Result(displayName, formattedMessage);
    }

    record Result(String displayName, String formattedMessage) {}
}
