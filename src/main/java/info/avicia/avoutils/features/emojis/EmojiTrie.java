package info.avicia.avoutils.features.emojis;

import java.util.HashMap;
import java.util.Map;

/**
 * A character-prefix tree (Trie) for emoji shortcode lookups
 */
public class EmojiTrie {

    private static class Node {
        final Map<Character, Node> children = new HashMap<>(4);
        String emojiValue = null;
    }

    private final Node root = new Node();
    private boolean empty = true;

    public void insert(String key, String value) {
        if (key == null || key.isEmpty())
            return;
        Node current = root;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            Node next = current.children.get(c);
            if (next == null) {
                next = new Node();
                current.children.put(c, next);
            }
            current = next;
        }
        current.emojiValue = value;
        empty = false;
    }

    public boolean isEmpty() {
        return empty;
    }

    /**
     * Search the trie for a string slice [start, end).
     * Caller guarantees: text != null, 0 <= start < end <= text.length().
     * Returns the emoji PUA value if found, null otherwise.
     */
    public String search(String text, int start, int end) {
        Node current = root;
        for (int i = start; i < end; i++) {
            current = current.children.get(text.charAt(i));
            if (current == null) {
                return null;
            }
        }
        return current.emojiValue;
    }
}
