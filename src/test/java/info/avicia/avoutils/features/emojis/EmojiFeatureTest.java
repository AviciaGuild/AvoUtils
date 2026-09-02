package info.avicia.avoutils.features.emojis;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmojiFeatureTest {

    @Test
    void safeNameForLowercasesAndReplacesInvalidCharacters() {
        assertEquals("hello_world_", EmojiFeature.safeNameFor("Hello World!"));
        assertEquals("cupboi", EmojiFeature.safeNameFor("CupBoi"));
    }

    @Test
    void safeNameForKeepsAllowedCharacters() {
        assertEquals("a.b-c_d", EmojiFeature.safeNameFor("a.b-c_d"));
    }

    @Test
    void buildTrieCombinesStandardAndCustomEmojis() {
        Map<String, String> standard = new HashMap<>();
        standard.put(":smile:", "PUA-1");
        Map<String, String> custom = new HashMap<>();
        custom.put(":avicia:", "PUA-2");

        EmojiTrie trie = EmojiFeature.buildTrie(standard, custom);

        assertEquals("PUA-1", trie.search(":smile:", 0, 7));
        assertEquals("PUA-2", trie.search(":avicia:", 0, 8));
        assertFalse(trie.isEmpty());
    }

    @Test
    void replaceUnicodeEmojisWithPuaMapsCodePoints() {
        Map<Integer, String> charToPua = new HashMap<>();
        charToPua.put(0x1F600, "\uE200");

        assertEquals("a\uE200b", EmojiFeature.replaceUnicodeEmojisWithPua("a\uD83D\uDE00b", charToPua));
    }

    @Test
    void replaceUnicodeEmojisWithPuaReturnsInputWhenNoMapping() {
        assertEquals("abc", EmojiFeature.replaceUnicodeEmojisWithPua("abc", new HashMap<>()));
        assertNull(EmojiFeature.replaceUnicodeEmojisWithPua(null, new HashMap<>()));
        assertEquals("", EmojiFeature.replaceUnicodeEmojisWithPua("", new HashMap<>()));
    }
}
