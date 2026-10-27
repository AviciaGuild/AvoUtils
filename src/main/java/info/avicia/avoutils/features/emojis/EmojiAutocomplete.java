package info.avicia.avoutils.features.emojis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Finds the emoji shortcode being typed and the shortcodes that complete it. */
public final class EmojiAutocomplete {
    private EmojiAutocomplete() {
    }

    public record AutocompletePrefix(int startIndex, String prefix) {
    }

    public static boolean isValidShortcodeChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_' || c == '+' || c == '-';
    }

    static boolean isAllowedBeforeShortcodeColon(char c) {
        return Character.isWhitespace(c)
                || c == ':'
                || c == '(' || c == '[' || c == '{'
                || c == '"' || c == '\'';
    }

    public static AutocompletePrefix findAutocompletePrefix(String textBeforeCursor) {
        if (textBeforeCursor == null || textBeforeCursor.isEmpty()) {
            return null;
        }

        int lastColon = textBeforeCursor.lastIndexOf(':');
        if (lastColon == -1) {
            return null;
        }

        String afterColon = textBeforeCursor.substring(lastColon + 1);
        for (int i = 0; i < afterColon.length(); i++) {
            char c = afterColon.charAt(i);
            if (!isValidShortcodeChar(c)) {
                return null;
            }
        }

        if (!afterColon.isEmpty()) {
            if (lastColon > 0 && !isAllowedBeforeShortcodeColon(textBeforeCursor.charAt(lastColon - 1))) {
                return null;
            }
            return new AutocompletePrefix(lastColon, ":" + afterColon);
        }

        // afterColon is empty: cursor is directly after the colon
        if (lastColon == 0 || isAllowedBeforeShortcodeColon(textBeforeCursor.charAt(lastColon - 1))) {
            return new AutocompletePrefix(lastColon, ":");
        }

        return null;
    }

    static List<String> matchShortcodes(Set<String> customKeys, Set<String> standardKeys, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return Collections.emptyList();
        }
        int prefixLen = prefix.length();
        List<String> matches = new ArrayList<>();

        if (customKeys != null) {
            for (String key : customKeys) {
                if (key.length() >= prefixLen && key.regionMatches(true, 0, prefix, 0, prefixLen)) {
                    matches.add(key);
                }
            }
        }
        Collections.sort(matches);

        if (matches.size() >= 50) {
            return matches.subList(0, 50);
        }

        List<String> twemojiMatches = new ArrayList<>();
        if (standardKeys != null) {
            for (String key : standardKeys) {
                if (customKeys != null && customKeys.contains(key)) {
                    continue;
                }
                if (key.length() >= prefixLen && key.regionMatches(true, 0, prefix, 0, prefixLen)) {
                    twemojiMatches.add(key);
                }
            }
        }
        Collections.sort(twemojiMatches);

        int remaining = 50 - matches.size();
        if (twemojiMatches.size() > remaining) {
            matches.addAll(twemojiMatches.subList(0, remaining));
        } else {
            matches.addAll(twemojiMatches);
        }

        return matches;
    }
}

