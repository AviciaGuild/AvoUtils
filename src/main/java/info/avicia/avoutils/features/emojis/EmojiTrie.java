package info.avicia.avoutils.features.emojis;

import java.util.Arrays;

/**
 * A character-prefix tree (Trie) for emoji shortcode lookups
 */
public class EmojiTrie {

    private static class Node {
        char[] keys = new char[0];
        Node[] values = new Node[0];
        String emojiValue = null;

        Node get(char c) {
            int idx = Arrays.binarySearch(keys, c);
            if (idx >= 0) {
                return values[idx];
            }
            return null;
        }

        void put(char c, Node node) {
            int idx = Arrays.binarySearch(keys, c);
            if (idx >= 0) {
                values[idx] = node;
            } else {
                int insertIdx = -idx - 1;
                char[] newKeys = new char[keys.length + 1];
                Node[] newValues = new Node[keys.length + 1];
                System.arraycopy(keys, 0, newKeys, 0, insertIdx);
                System.arraycopy(values, 0, newValues, 0, insertIdx);
                newKeys[insertIdx] = c;
                newValues[insertIdx] = node;
                System.arraycopy(keys, insertIdx, newKeys, insertIdx + 1, keys.length - insertIdx);
                System.arraycopy(values, insertIdx, newValues, insertIdx + 1, keys.length - insertIdx);
                keys = newKeys;
                values = newValues;
            }
        }
    }

    private final Node root = new Node();

    public void insert(String key, String value) {
        if (key == null || key.isEmpty()) return;
        Node current = root;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            Node next = current.get(c);
            if (next == null) {
                next = new Node();
                current.put(c, next);
            }
            current = next;
        }
        current.emojiValue = value;
    }

    public boolean isEmpty() {
        return root.keys.length == 0;
    }

    /**
     * Search the trie for a string slice [start, end)
     */
    public String search(String text, int start, int end) {
        if (text == null || start < 0 || end > text.length() || start >= end) {
            return null;
        }
        Node current = root;
        for (int i = start; i < end; i++) {
            current = current.get(text.charAt(i));
            if (current == null) {
                return null;
            }
        }
        return current.emojiValue;
    }
}
