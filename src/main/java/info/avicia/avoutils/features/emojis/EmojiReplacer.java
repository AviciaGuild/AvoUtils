package info.avicia.avoutils.features.emojis;

import info.avicia.avoutils.AvoUtilsMod;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Replaces emoji shortcodes in Text components
 */
public class EmojiReplacer {

    private static final TextColor EMOJI_COLOR = TextColor.fromRgb(0xFFFFFF);

    private static final boolean[] VALID_SHORTCODE = new boolean[128];
    static {
        for (char c = 'a'; c <= 'z'; c++)
            VALID_SHORTCODE[c] = true;
        for (char c = 'A'; c <= 'Z'; c++)
            VALID_SHORTCODE[c] = true;
        for (char c = '0'; c <= '9'; c++)
            VALID_SHORTCODE[c] = true;
        VALID_SHORTCODE['_'] = true;
        VALID_SHORTCODE['+'] = true;
        VALID_SHORTCODE['-'] = true;
    }

    private static boolean isValidShortcodeChar(char c) {
        return c < 128 && VALID_SHORTCODE[c];
    }

    public static Text replace(Text text) {
        if (text == null)
            return null;

        EmojiFeature feature = AvoUtilsMod.getInstance().getFeature(EmojiFeature.class);
        if (feature == null || !feature.isEnabled())
            return text;

        EmojiTrie trie = feature.getActiveTrie();
        if (trie.isEmpty())
            return text;

        // Skip strings with no valid shortcode pattern
        if (!hasShortcodeCandidates(text.getString()))
            return text;

        MutableText result = Text.empty();
        AtomicBoolean modified = new AtomicBoolean(false);

        text.visit((style, str) -> {
            if (str.isEmpty())
                return Optional.empty();

            // Unicode → PUA conversion for native emoji characters
            String converted = feature.replaceUnicodeEmojisWithPua(str);

            // Replace :shortcode: patterns with PUA chars
            MutableText replaced = replaceShortcodes(converted, trie, style);
            if (replaced != null) {
                result.append(replaced);
                modified.set(true);
            } else {
                result.append(Text.literal(converted).setStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);

        return modified.get() ? result : text;
    }

    private static boolean hasShortcodeCandidates(String text) {
        int len = text.length();
        int colon = -1;
        for (int i = 0; i < len; i++) {
            if (text.charAt(i) == ':') {
                if (colon == -1) {
                    colon = i;
                } else {
                    // Found a closing colon; verify at least one valid char between them
                    if (i > colon + 1) {
                        for (int j = colon + 1; j < i; j++) {
                            if (isValidShortcodeChar(text.charAt(j))) {
                                return true;
                            }
                        }
                    }
                    colon = -1; // Reset for next pair
                }
            }
        }
        return false;
    }

    /**
     * State-machine based shortcode replacement.
     * Returns null if no replacements were made.
     */
    private static MutableText replaceShortcodes(String text, EmojiTrie trie, Style parentStyle) {
        int len = text.length();
        int firstColon = text.indexOf(':');
        if (firstColon == -1 || firstColon == len - 1)
            return null;

        MutableText root = null;
        int lastEnd = 0;
        int i = firstColon;

        while (i < len) {
            if (text.charAt(i) == ':') {
                int end = text.indexOf(':', i + 1);
                if (end == -1)
                    break;

                // Validate token characters between the colons
                boolean valid = end > i + 1;
                if (valid) {
                    for (int j = i + 1; j < end; j++) {
                        if (!isValidShortcodeChar(text.charAt(j))) {
                            valid = false;
                            break;
                        }
                    }
                }

                if (valid) {
                    String replacement = trie.search(text, i, end + 1);
                    if (replacement != null) {
                        if (root == null)
                            root = Text.empty();

                        // Flush text before this token
                        if (i > lastEnd) {
                            root.append(Text.literal(text.substring(lastEnd, i)).setStyle(parentStyle));
                        }

                        // Append emoji with white color to ensure proper bitmap font rendering
                        Style emojiStyle = parentStyle.withColor(EMOJI_COLOR);
                        root.append(Text.literal(replacement).setStyle(emojiStyle));

                        lastEnd = end + 1;
                        i = end;
                        continue;
                    }
                }
                i = end;
            }
            i++;
        }

        if (root == null)
            return null;

        if (lastEnd < len) {
            root.append(Text.literal(text.substring(lastEnd)).setStyle(parentStyle));
        }

        return root;
    }
}
