package info.avicia.avoutils.features.chatbridge;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.HoverEvent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves displayed names to real Minecraft usernames using hover text
 * from the chat component tree. Searches for a component whose visible
 * text contains the displayed name, then extracts hover from it.
 */
final class UsernameResolver {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]{3,16}");

    private static final Pattern HOVER_REAL_NAME_PATTERN = Pattern.compile(
            "(?:'(?:s)? real name is\\s+|Real Username:\\s*)([a-zA-Z0-9_]{3,16})",
            Pattern.CASE_INSENSITIVE);

    static boolean isValid(String name) {
        return name != null && USERNAME_PATTERN.matcher(name).matches();
    }

    /**
     * Finds the component whose visible text contains displayedName,
     * extracts hover real name from it. Falls back to displayedName
     * if it's a valid username and no hover is found.
     */
    static String resolve(Text message, String displayedName) {
        String hover = findHoverForName(message, displayedName);
        if (hover != null) return hover;

        if (isValid(displayedName)) return displayedName;

        return null;
    }

    private static String findHoverForName(Text text, String name) {
        if (text == null || name == null) return null;

        // If this is a leaf (no siblings), check if the displayed name
        // starts with this component's text. The first leaf that contributes
        // to the displayed name carries the hover
        List<Text> siblings = text.getSiblings();
        if (siblings.isEmpty()) {
            String visible = text.getString();
            if (!visible.isEmpty() && name.startsWith(visible)) {
                String hover = extractHoverRealName(text.getStyle());
                if (hover != null) return hover;
            }
            return null;
        }

        // Recurse into children
        for (Text sibling : siblings) {
            String found = findHoverForName(sibling, name);
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
}
