package info.avicia.avoutils.features.emojis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.network.chat.Component;

class EmojiReplacerTest {

    @Test
    void replaceReturnsNullForNullInput() {
        assertNull(EmojiReplacer.replace(null));
        assertNull(EmojiReplacer.replace(null, new EmojiTrie()));
    }

    @Test
    void replaceReturnsSameInstanceWhenNoColonsPresent() {
        Component text = Component.literal("This is a message with no colons at all.");
        assertSame(text, EmojiReplacer.replace(text));
        assertSame(text, EmojiReplacer.replace(text, new EmojiTrie()));
    }

    @Test
    void replaceReturnsSameInstanceForSingleColon() {
        Component text = Component.literal("PlayerName: Hello world!");
        assertSame(text, EmojiReplacer.replace(text));
        assertSame(text, EmojiReplacer.replace(text, new EmojiTrie()));
    }

    @Test
    void replaceLeavesRawUnicodeEmojisUntouched() {
        EmojiTrie trie = new EmojiTrie();
        trie.insert(":catwat:", "\uDB80\uDC00");

        Component text = Component.literal("Good game! 😀 ❤️ 👍");
        assertSame(text, EmojiReplacer.replace(text));
        assertSame(text, EmojiReplacer.replace(text, trie));
    }

    @Test
    void replaceSubstitutesKnownShortcodeWithTrieMapping() {
        EmojiTrie trie = new EmojiTrie();
        String pua = Character.toString(0xF0000);
        trie.insert(":aga:", pua);

        Component text = Component.literal("Look at this :aga: emoji!");
        Component replaced = EmojiReplacer.replace(text, trie);

        assertNotNull(replaced);
        assertEquals("Look at this " + pua + " emoji!", replaced.getString());
    }

    @Test
    void replacePreservesTextWhenShortcodeUnknownOrInvalid() {
        EmojiTrie trie = new EmojiTrie();
        trie.insert(":catwat:", "\uDB80\uDC00");

        Component unknown = Component.literal("Unknown :notreal: shortcode");
        assertSame(unknown, EmojiReplacer.replace(unknown, trie));

        Component invalid = Component.literal("Not a shortcode :hello world: here");
        assertSame(invalid, EmojiReplacer.replace(invalid, trie));
    }

    @Test
    void replaceHandlesAdjacentAndConsecutiveColons() {
        EmojiTrie trie = new EmojiTrie();
        String pua = Character.toString(0xF0000);
        trie.insert(":aga:", pua);

        Component adjacent = Component.literal("::aga:");
        Component replacedAdjacent = EmojiReplacer.replace(adjacent, trie);
        assertNotNull(replacedAdjacent);
        assertEquals(":" + pua, replacedAdjacent.getString());

        Component afterUnknown = Component.literal(":unknown::aga:");
        Component replacedAfterUnknown = EmojiReplacer.replace(afterUnknown, trie);
        assertNotNull(replacedAfterUnknown);
        assertEquals(":unknown:" + pua, replacedAfterUnknown.getString());
    }
}

