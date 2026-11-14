package info.avicia.avoutils.features.chatbridge;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.UsernameResolver;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/**
 * Detects player-authored guild chat lines, resolves class nicknames to real
 * Minecraft usernames via hover text, and computes normalized dedup keys.
 */
final class GuildChatDetector {

    private static final Pattern CHAT_PATTERN = Pattern.compile(
            "^(?:<\\d+>\\s*)?([a-zA-Z0-9_][a-zA-Z0-9_ ]*[a-zA-Z0-9_]|[a-zA-Z0-9_]{3,16})\\s*:\\s*(.*)$",
            Pattern.DOTALL);
    private static final Pattern EMOJI_SHORTCODE_PATTERN = Pattern.compile(":[a-zA-Z0-9_+\\-]+:");

    private GuildChatDetector() {
    }

    static Result tryDetect(String cleaned, Component message) {
        if (cleaned == null || cleaned.isEmpty()) {
            return null;
        }

        Matcher matcher = CHAT_PATTERN.matcher(cleaned);
        if (!matcher.find()) {
            return null;
        }

        String displayedName = matcher.group(1).trim();
        String content = matcher.group(2).trim();
        if (content.isEmpty()) {
            return null;
        }

        String realUsername = UsernameResolver.resolve(message, displayedName);
        if (realUsername == null) {
            AvoUtilsMod.LOGGER.warn("[AvoUtils] [ChatBridge] Could not resolve username from '{}'", displayedName);
            return null;
        }

        String dedupContent = EMOJI_SHORTCODE_PATTERN.matcher(content)
                .replaceAll("")
                .replaceAll("\\s+", " ")
                .trim();
        String dedupKey = realUsername + "\u0000" + dedupContent;

        return new Result(realUsername, content, dedupKey);
    }

    record Result(String username, String content, String dedupKey) {}
}
