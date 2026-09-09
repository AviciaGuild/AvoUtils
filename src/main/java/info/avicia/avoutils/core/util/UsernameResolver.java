package info.avicia.avoutils.core.util;

import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves displayed names (or class nicknames) to real Minecraft usernames
 * using hover text from the chat component tree.
 */
public final class UsernameResolver {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    private static final Pattern HOVER_REAL_NAME_PATTERN = Pattern.compile(
            "(?:'(?:s)? real name is\\s+|Real Username:\\s*)([a-zA-Z0-9_]{3,16})",
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
    public static String resolve(Text message, String displayedName) {
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
     * Searches the component tree for a hover event containing a real username.
     * Checks spans that match or overlap displayedName, then falls back to any
     * real username hover in the message.
     */
    public static String findHoverForName(Text message, String name) {
        if (message == null) return null;

        String targetName = name != null ? name.trim().toLowerCase(Locale.ROOT) : "";

        AtomicReference<String> matchedHover = new AtomicReference<>(null);

        message.visit((style, spanText) -> {
            String hoverName = extractHoverRealName(style);
            if (hoverName != null) {
                if (!targetName.isEmpty()) {
                    String spanTrimmed = spanText.trim().toLowerCase(Locale.ROOT);
                    if (!spanTrimmed.isEmpty() && (targetName.contains(spanTrimmed) || spanTrimmed.contains(targetName))) {
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

    public static String extractHoverRealName(Style style) {
        if (style == null) return null;
        HoverEvent hoverEvent = style.getHoverEvent();
        if (!(hoverEvent instanceof HoverEvent.ShowText showTextEvent)) return null;
        Text hoverComponent = showTextEvent.value();
        if (hoverComponent == null) return null;
        String hoverText = hoverComponent.getString()
                .replace('\u2019', '\'')
                .replace('\u2018', '\'');
        Matcher matcher = HOVER_REAL_NAME_PATTERN.matcher(hoverText);
        return matcher.find() ? matcher.group(1) : null;
    }
}

