package info.avicia.avoutils.features.chatbridge;

import net.minecraft.text.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects guild reward grants (Emeralds, Aspects, Guild Tomes) from system
 * chat and returns data for both Discord relay and storage delta tracking.
 */
final class RewardDetector {

    private static final Pattern REWARD_GRANT_PATTERN = Pattern.compile(
            "^(?<sender>.+?)\\s+rewarded\\s+(?:(?<emeraldAmount>[\\d, ]+)\\s+Emeralds?|(?<aspectAmount>an?|[\\d, ]+)\\s+Aspects?|(?<tomeAmount>an?|[\\d, ]+)\\s+Guild Tomes?)\\s+to\\s+(?<recipient>.+)$",
            Pattern.CASE_INSENSITIVE);

    record RewardResult(String formattedMessage, String senderDisplay, String recipientDisplay,
                        long emeraldAmount, long aspectAmount, long tomeCount) {}

    static RewardResult tryDetect(String cleaned, Text message) {
        if (cleaned == null || !cleaned.contains("rewarded") || !cleaned.contains(" to ")) return null;

        Matcher m = REWARD_GRANT_PATTERN.matcher(cleaned);
        if (!m.matches()) return null;

        String senderDisplay = m.group("sender").trim();
        String recipientDisplay = m.group("recipient").trim();

        String sender = UsernameResolver.resolve(message, senderDisplay);
        if (sender == null) sender = senderDisplay;
        String recipient = UsernameResolver.resolve(message, recipientDisplay);
        if (recipient == null) recipient = recipientDisplay;

        String emeraldAmount = m.group("emeraldAmount");
        if (emeraldAmount != null) {
            long amount = parseNumber(emeraldAmount);
            String formatted = "**" + sender + "** rewarded **" + String.format("%,d", amount)
                    + " Emeralds** to **" + recipient + "**";
            return new RewardResult(formatted, sender, recipient, amount, 0, 0);
        }

        String aspectAmount = m.group("aspectAmount");
        if (aspectAmount != null) {
            long amount = parseArticleAmount(aspectAmount);
            if (amount <= 0) return null;
            String formatted = "**" + sender + "** rewarded **" + amount + " Aspect"
                    + (amount != 1 ? "s" : "") + "** to **" + recipient + "**";
            return new RewardResult(formatted, sender, recipient, 0, amount, 0);
        }

        String tomeAmount = m.group("tomeAmount");
        if (tomeAmount != null) {
            long amount = parseArticleAmount(tomeAmount);
            if (amount <= 0) return null;
            String formatted = "**" + sender + "** rewarded **" + amount + " Guild Tome"
                    + (amount != 1 ? "s" : "") + "** to **" + recipient + "**";
            return new RewardResult(formatted, sender, recipient, 0, 0, amount);
        }

        return null;
    }

    private static long parseNumber(String raw) {
        return Long.parseLong(raw.replace(",", "").replace(" ", "").trim());
    }

    private static long parseArticleAmount(String raw) {
        if (raw == null) return 0;
        String t = raw.trim().toLowerCase();
        if (t.equals("a") || t.equals("an")) return 1;
        return parseNumber(raw);
    }
}
