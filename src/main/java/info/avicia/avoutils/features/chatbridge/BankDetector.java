package info.avicia.avoutils.features.chatbridge;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.UsernameResolver;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/**
 * Detects guild bank deposit/withdrawal messages and extracts structured fields for gateway relay.
 */
final class BankDetector {

    private static final Pattern BANK_PATTERN = Pattern.compile(
            "^(.+?)\\s+(deposited|withdrew)\\s+(.+?)\\s+(to|from)\\s+the Guild Bank\\s+\\((.+)\\)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BANK_NO_TIER_PATTERN = Pattern.compile(
            "^(.+?)\\s+(deposited|withdrew)\\s+(.+?)\\s+(to|from)\\s+the Guild(?: Bank)?$",
            Pattern.CASE_INSENSITIVE);

    private BankDetector() {
    }

    static Result tryDetect(String cleaned, Component message) {
        if (cleaned == null || !cleaned.contains("Guild")
                || (!cleaned.contains("deposited") && !cleaned.contains("withdrew"))) {
            return null;
        }

        Matcher matcher = BANK_PATTERN.matcher(cleaned);
        String tier;
        if (matcher.matches()) {
            String rawTier = matcher.group(5).trim();
            tier = rawTier.isEmpty() || "Unknown".equalsIgnoreCase(rawTier) ? null : rawTier;
        } else {
            matcher = BANK_NO_TIER_PATTERN.matcher(cleaned);
            if (!matcher.matches()) return null;
            tier = null;
        }

        String displayedPlayer = matcher.group(1).trim();
        if (displayedPlayer.contains(":")) return null;
        String action = matcher.group(2).toLowerCase(Locale.ROOT);
        String item = matcher.group(3).trim();
        if (displayedPlayer.isEmpty() || item.isEmpty()) return null;

        String realUsername = UsernameResolver.resolve(message, displayedPlayer);
        if (realUsername == null) {
            AvoUtilsMod.LOGGER.warn("[AvoUtils] [ChatBridge/Bank] Could not resolve username from '{}'", displayedPlayer);
            return null;
        }

        AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Bank] Detected: {} {} {} (tier={})",
                realUsername, action, item, tier);

        return new Result(realUsername, action, item, tier);
    }

    record Result(String player, String action, String item, String tier) {}
}
