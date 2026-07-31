package info.avicia.avoutils.features.chatbridge;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.HoverEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Username resolution from Wynncraft chat components.
 * Extracts real Minecraft usernames from hover text ("real name is X").
 */
final class UsernameResolver {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]{3,16}");

    private static final Pattern HOVER_REAL_NAME_PATTERN = Pattern.compile(
            "(?:'(?:s)? real name is\\s+|Real Username:\\s*)([a-zA-Z0-9_]{3,16})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PARENTHESIZED_USERNAME_PATTERN =
            Pattern.compile(".*\\(([a-zA-Z0-9_]{3,16})\\)$");

    static boolean isValid(String name) {
        return name != null && USERNAME_PATTERN.matcher(name).matches();
    }

    /**
     * Resolves a displayed name (which may be a nickname) to a real username
     * by searching the component tree for hover text and insertion hints.
     */
    static String resolve(Text message, String displayedName) {
        // Try hover real name
        String hoverRealName = findHoverRealName(message);
        if (hoverRealName != null) return hoverRealName;

        // Try parenthesized real name (e.g. "Nickname (RealName)")
        Matcher parenMatcher = PARENTHESIZED_USERNAME_PATTERN.matcher(displayedName);
        if (parenMatcher.matches()) return parenMatcher.group(1);

        // If displayed name is a valid username, use it
        if (isValid(displayedName)) return displayedName;

        // Try insertion username
        String insertionName = findInsertionName(message);
        if (insertionName != null) return insertionName;

        return null;
    }

    private static String findHoverRealName(Text text) {
        if (text == null) return null;
        String fromStyle = extractHoverRealName(text.getStyle());
        if (fromStyle != null) return fromStyle;
        for (Text sibling : text.getSiblings()) {
            String found = findHoverRealName(sibling);
            if (found != null) return found;
        }
        return null;
    }

    private static String extractHoverRealName(Style style) {
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

    private static String findInsertionName(Text text) {
        if (text == null) return null;
        String insertion = text.getStyle().getInsertion();
        if (isValid(insertion)) return insertion;
        for (Text sibling : text.getSiblings()) {
            String found = findInsertionName(sibling);
            if (found != null) return found;
        }
        return null;
    }
}
