package info.avicia.avoutils.core.util;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

/**
 * Resolves displayed names (or class nicknames) to real Minecraft usernames
 * using hover text from the chat component tree.
 */
public final class UsernameResolver {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    private static final Pattern HOVER_REAL_NAME_PATTERN = Pattern.compile(
            "(?:'(?:s)? real (?:user)?name is\\s+|Real Username:\\s*)([a-zA-Z0-9_]{3,16})",
            Pattern.CASE_INSENSITIVE);

    private UsernameResolver() {
    }

    public static boolean isValid(String name) {
        return name != null && USERNAME_PATTERN.matcher(name.trim()).matches();
    }

    /**
     * Resolves a displayed name to a real Minecraft username.
     * Checks hover text in the message component first.
     * Falls back to displayedName if it is already a valid Minecraft username.
     */
    public static String resolve(Component message, String displayedName) {
        if (message != null) {
            String hover = findHoverForName(message, displayedName);
            if (hover != null) {
                return hover;
            }
        }

        if (displayedName != null && isValid(displayedName)) {
            return displayedName.trim();
        }

        return null;
    }

    /**
     * Returns the real username from the hover of the span that shows {@code name}, or null if there is
     * none. The span's text and the name match when either contains the other as a whole word, so "Bob"
     * matches "[Chief] Bob" but not "Bobby".
     */
    public static String findHoverForName(Component message, String name) {
        if (message == null) return null;

        String targetName = name != null ? name.trim().toLowerCase(Locale.ROOT) : "";

        AtomicReference<String> matchedHover = new AtomicReference<>(null);

        message.visit((style, spanText) -> {
            String hoverName = extractHoverRealName(style);
            if (hoverName != null) {
                if (!targetName.isEmpty()) {
                    String spanTrimmed = PacketTextNormalizer.normalizeForParsing(spanText).toLowerCase(Locale.ROOT);
                    if (!spanTrimmed.isEmpty()
                            && (containsWord(targetName, spanTrimmed) || containsWord(spanTrimmed, targetName))) {
                        matchedHover.set(hoverName);
                        return Optional.of(true); // stop visiting
                    }
                }
            }
            return Optional.empty();
        }, Style.EMPTY);

        if (matchedHover.get() != null) {
            return matchedHover.get();
        }

        return null;
    }

    /** Whether {@code part} occurs in {@code text} without a letter, digit or underscore right next to it. */
    private static boolean containsWord(String text, String part) {
        for (int start = text.indexOf(part); start >= 0; start = text.indexOf(part, start + 1)) {
            int end = start + part.length();
            if ((start == 0 || !isWordChar(text.charAt(start - 1)))
                    && (end == text.length() || !isWordChar(text.charAt(end)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public static String extractHoverRealName(Style style) {
        if (style == null) return null;
        HoverEvent hoverEvent = style.getHoverEvent();
        if (!(hoverEvent instanceof HoverEvent.ShowText showTextEvent)) return null;
        Component hoverComponent = showTextEvent.value();
        if (hoverComponent == null) return null;
        String hoverText = PacketTextNormalizer.normalizeForParsing(hoverComponent.getString())
                .replace('\u2019', '\'')
                .replace('\u2018', '\'');
        Matcher matcher = HOVER_REAL_NAME_PATTERN.matcher(hoverText);
        return matcher.find() ? matcher.group(1) : null;
    }
}

