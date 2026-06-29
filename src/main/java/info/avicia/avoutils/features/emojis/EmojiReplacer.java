package info.avicia.avoutils.features.emojis;

import info.avicia.avoutils.AvoUtilsMod;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.text.Style;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EmojiReplacer {

    /**
     * Replaces emoji shortcodes in the given Text component
     * Returns the original Text component reference if no replacements were made
     */
    public static Text replace(Text text) {
        if (text == null) return null;

        EmojiFeature feature = AvoUtilsMod.getInstance().getFeature(EmojiFeature.class);
        if (feature == null) return text;

        EmojiTrie trie = feature.getActiveTrie();
        if (trie.isEmpty()) return text;

        return replace(text, trie);
    }

    private static Text replace(Text text, EmojiTrie trie) {
        if (text == null) return null;

        boolean modified = false;
        MutableText result = null;

        // Process main content
        if (text.getContent() instanceof PlainTextContent literalContent) {
            MutableText replaced = replaceEmojiShortcodes(literalContent.string(), trie, text.getStyle());
            if (replaced != null) {
                result = replaced;
                modified = true;
            }
        } else if (text.getContent() instanceof TranslatableTextContent translatableContent) {
            Object[] args = translatableContent.getArgs();
            Object[] processedArgs = null;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                Object replacedArg = null;
                if (arg instanceof Text textArg) {
                    Text replaced = replace(textArg, trie);
                    if (replaced != textArg) {
                        replacedArg = replaced;
                    }
                } else if (arg instanceof String strArg) {
                    replacedArg = replaceEmojiShortcodes(strArg, trie, text.getStyle());
                }

                if (replacedArg != null) {
                    if (processedArgs == null) {
                        processedArgs = Arrays.copyOf(args, args.length);
                    }
                    processedArgs[i] = replacedArg;
                    modified = true;
                }
            }
            if (modified) {
                result = Text.translatable(translatableContent.getKey(), processedArgs).setStyle(text.getStyle());
            }
        }

        // Process siblings recursively
        List<Text> siblings = text.getSiblings();
        int size = siblings.size();
        List<Text> processedSiblings = null;
        for (int i = 0; i < size; i++) {
            Text sibling = siblings.get(i);
            Text replacedSibling = replace(sibling, trie);
            if (replacedSibling != sibling) {
                if (processedSiblings == null) {
                    processedSiblings = new ArrayList<>(siblings.subList(0, i));
                }
                processedSiblings.add(replacedSibling);
                modified = true;
            } else if (processedSiblings != null) {
                processedSiblings.add(sibling);
            }
        }

        if (!modified) {
            return text;
        }

        // If content did not change but siblings did, clone the parent node content
        if (result == null) {
            result = MutableText.of(text.getContent()).setStyle(text.getStyle());
        }

        // Append siblings
        if (processedSiblings != null) {
            for (Text child : processedSiblings) {
                result.append(child);
            }
        } else {
            for (Text sibling : siblings) {
                result.append(sibling);
            }
        }

        return result;
    }

    /**
     * Scans a string for emoji shortcodes and replaces them
     * Returns null if no replacements were made
     */
    private static MutableText replaceEmojiShortcodes(String text, EmojiTrie trie, Style parentStyle) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int len = text.length();
        int start = text.indexOf(':');
        if (start == -1 || start == len - 1) {
            return null;
        }

        MutableText root = null;
        int lastIndex = 0;
        int current = start;

        while (current < len - 2) {
            int nextColon = text.indexOf(':', current + 1);
            if (nextColon == -1) {
                break;
            }
            int diff = nextColon - current;
            if (diff >= 2) {
                boolean valid = true;
                for (int i = current + 1; i < nextColon; i++) {
                    if (!isValidShortcodeChar(text.charAt(i))) {
                        valid = false;
                        break;
                    }
                }
                if (valid) {
                    String replacement = trie.search(text, current, nextColon + 1);
                    if (replacement != null) {
                        if (root == null) {
                            root = Text.empty();
                        }
                        if (current > lastIndex) {
                            root.append(Text.literal(text.substring(lastIndex, current)).setStyle(parentStyle));
                        }
                        
                        Style emojiStyle = parentStyle.withColor(net.minecraft.text.TextColor.fromRgb(0xFFFFFF));
                        root.append(Text.literal(replacement).setStyle(emojiStyle));
                        
                        lastIndex = nextColon + 1;
                        current = nextColon;
                        continue;
                    }
                }
            }
            current = nextColon;
        }

        if (root == null) {
            return null;
        }

        if (lastIndex < len) {
            root.append(Text.literal(text.substring(lastIndex)).setStyle(parentStyle));
        }

        return root;
    }

    private static boolean isValidShortcodeChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '+' || c == '-';
    }
}
