package info.avicia.avoutils.features.emojis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmojiTrieTest {

    @Test
    void trieStartsEmpty() {
        EmojiTrie trie = new EmojiTrie();
        assertTrue(trie.isEmpty());
    }

    @Test
    void insertAndSearchSingleKey() {
        EmojiTrie trie = new EmojiTrie();
        trie.insert(":smile:", "PUA-1");
        assertFalse(trie.isEmpty());
        assertEquals("PUA-1", trie.search(":smile:", 0, 7));
    }

    @Test
    void insertIgnoresNullAndEmptyKeys() {
        EmojiTrie trie = new EmojiTrie();
        trie.insert(null, "X");
        trie.insert("", "X");
        assertTrue(trie.isEmpty());
        assertNull(trie.search("", 0, 0));
    }

    @Test
    void searchReturnsNullForUnknownOrPartialKeys() {
        EmojiTrie trie = new EmojiTrie();
        trie.insert(":smile:", "PUA-1");
        assertNull(trie.search(":frown:", 0, 7));
        assertNull(trie.search(":smile", 0, 6)); // prefix, not a full key
        assertNull(trie.search(":smile:extra", 0, 12)); // longer than key
    }

    @Test
    void searchHonorsSliceBounds() {
        EmojiTrie trie = new EmojiTrie();
        trie.insert(":smile:", "PUA-1");
        String text = "hi :smile: bye";
        assertEquals("PUA-1", trie.search(text, 3, 10));
    }

    @Test
    void insertOverwritesExistingKey() {
        EmojiTrie trie = new EmojiTrie();
        trie.insert(":smile:", "PUA-1");
        trie.insert(":smile:", "PUA-2");
        assertEquals("PUA-2", trie.search(":smile:", 0, 7));
    }

    @Test
    void multipleDistinctKeysCoexist() {
        EmojiTrie trie = new EmojiTrie();
        trie.insert(":smile:", "A");
        trie.insert(":slight_smile:", "B");
        assertEquals("A", trie.search(":smile:", 0, 7));
        assertEquals("B", trie.search(":slight_smile:", 0, 14));
    }
}
