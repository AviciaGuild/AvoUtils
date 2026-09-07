package info.avicia.avoutils.features.emojis;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EmojiReplacerTest {

    @Test
    void replaceReturnsNullForNullInput() {
        assertNull(EmojiReplacer.replace(null));
        assertNull(EmojiReplacer.replace(null, new EmojiTrie()));
    }

    @Test
    void replaceReturnsSameInstanceWhenNoColonsPresent() {
        Text text = Text.literal("This is a message with no colons at all.");
        assertSame(text, EmojiReplacer.replace(text));
        assertSame(text, EmojiReplacer.replace(text, new EmojiTrie()));
    }

    @Test
    void replaceReturnsSameInstanceForSingleColon() {
        Text text = Text.literal("PlayerName: Hello world!");
        assertSame(text, EmojiReplacer.replace(text));
        assertSame(text, EmojiReplacer.replace(text, new EmojiTrie()));
    }

    @Test
    void replaceLeavesRawUnicodeEmojisUntouched() {
        EmojiTrie trie = new EmojiTrie();
        trie.insert(":catwat:", "\uDB80\uDC00");

        Text text = Text.literal("Good game! 😀 ❤️ 👍");
        assertSame(text, EmojiReplacer.replace(text));
        assertSame(text, EmojiReplacer.replace(text, trie));
    }

    @Test
    void replaceSubstitutesKnownShortcodeWithTrieMapping() {
        EmojiTrie trie = new EmojiTrie();
        String pua = Character.toString(0xF0000);
        trie.insert(":aga:", pua);

        Text text = Text.literal("Look at this :aga: emoji!");
        Text replaced = EmojiReplacer.replace(text, trie);

        assertNotNull(replaced);
        assertEquals("Look at this " + pua + " emoji!", replaced.getString());
    }

    @Test
    void replacePreservesTextWhenShortcodeUnknownOrInvalid() {
        EmojiTrie trie = new EmojiTrie();
        trie.insert(":catwat:", "\uDB80\uDC00");

        Text unknown = Text.literal("Unknown :notreal: shortcode");
        assertSame(unknown, EmojiReplacer.replace(unknown, trie));

        Text invalid = Text.literal("Not a shortcode :hello world: here");
        assertSame(invalid, EmojiReplacer.replace(invalid, trie));
    }

    @Test
    void replaceHandlesAdjacentAndConsecutiveColons() {
        EmojiTrie trie = new EmojiTrie();
        String pua = Character.toString(0xF0000);
        trie.insert(":aga:", pua);

        Text adjacent = Text.literal("::aga:");
        Text replacedAdjacent = EmojiReplacer.replace(adjacent, trie);
        assertNotNull(replacedAdjacent);
        assertEquals(":" + pua, replacedAdjacent.getString());

        Text afterUnknown = Text.literal(":unknown::aga:");
        Text replacedAfterUnknown = EmojiReplacer.replace(afterUnknown, trie);
        assertNotNull(replacedAfterUnknown);
        assertEquals(":unknown:" + pua, replacedAfterUnknown.getString());
    }
}

