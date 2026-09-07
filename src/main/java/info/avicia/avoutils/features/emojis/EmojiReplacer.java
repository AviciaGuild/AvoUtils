package info.avicia.avoutils.features.emojis;

import info.avicia.avoutils.AvoUtilsMod;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.Optional;

/**
 * Replaces emoji shortcodes in Text components
 */
public class EmojiReplacer {

    private static final TextColor EMOJI_COLOR = TextColor.fromRgb(0xFFFFFF);

    private static boolean isValidShortcodeChar(char c) {
        return EmojiFeature.isValidShortcodeChar(c);
    }

    public static Text replace(Text text) {
        if (text == null)
            return null;

        AvoUtilsMod mod = AvoUtilsMod.getInstance();
        if (mod == null)
            return text;

        EmojiFeature feature = mod.getFeature(EmojiFeature.class);
        if (feature == null || !feature.isEnabled())
            return text;

        return replace(text, feature.getActiveTrie());
    }

    static Text replace(Text text, EmojiTrie trie) {
        if (text == null)
            return null;
        if (trie == null || trie.isEmpty())
            return text;

        String raw = text.getString();
        int firstColon = raw.indexOf(':');
        if (firstColon == -1 || firstColon == raw.length() - 1 || raw.indexOf(':', firstColon + 1) == -1) {
            return text;
        }

        MutableText result = Text.empty();
        boolean[] modified = { false };

        text.visit((style, str) -> {
            if (str.isEmpty())
                return Optional.empty();

            MutableText replaced = replaceShortcodes(str, trie, style);
            if (replaced != null) {
                result.append(replaced);
                modified[0] = true;
            } else {
                result.append(Text.literal(str).setStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);

        return modified[0] ? result : text;
    }

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

                        if (i > lastEnd) {
                            root.append(Text.literal(text.substring(lastEnd, i)).setStyle(parentStyle));
                        }

                        String emojiName = text.substring(i + 1, end);
                        Style emojiStyle = parentStyle.withColor(EMOJI_COLOR)
                                .withHoverEvent(EmojiTooltipHelper.createEmojiHover(replacement, emojiName));
                        root.append(Text.literal(replacement).setStyle(emojiStyle));

                        lastEnd = end + 1;
                        i = end + 1;
                        continue;
                    }
                }
                i = end;
                continue;
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
