package info.avicia.avoutils.features.emojis;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmojiAutocompleteTest {

    @Test
    void matchShortcodesFindsPrefixMatchesCaseInsensitively() {
        Set<String> custom = Set.of(":aga:", ":agabounce:", ":catwat:", ":Avicia:");
        Set<String> standard = Set.of(":apple:", ":agree:");

        List<String> matches = EmojiFeature.matchShortcodes(custom, standard, ":ag");
        // Custom emojis sorted first (:aga:, :agabounce:), then standard (:agree:)
        assertEquals(List.of(":aga:", ":agabounce:", ":agree:"), matches);

        List<String> upperMatches = EmojiFeature.matchShortcodes(custom, standard, ":AV");
        assertEquals(List.of(":Avicia:"), upperMatches);
    }

    @Test
    void matchShortcodesReturnsEmptyOnNoMatchOrEmptyPrefix() {
        Set<String> custom = Set.of(":aga:", ":albin:");
        Set<String> standard = Set.of(":smile:");

        assertTrue(EmojiFeature.matchShortcodes(custom, standard, null).isEmpty());
        assertTrue(EmojiFeature.matchShortcodes(custom, standard, "").isEmpty());
        assertTrue(EmojiFeature.matchShortcodes(custom, standard, ":xyz").isEmpty());
    }

    @Test
    void matchShortcodesCapsAtFifty() {
        Set<String> custom = new HashSet<>();
        for (int i = 0; i < 60; i++) {
            custom.add(String.format(":test%02d:", i));
        }

        List<String> matches = EmojiFeature.matchShortcodes(custom, Set.of(), ":test");
        assertEquals(50, matches.size());
    }

    @Test
    void findAutocompletePrefixHandlesAdjacentColons() {
        // Direct adjacent colon with letters typed: "::aga"
        EmojiFeature.AutocompletePrefix doubleColon = EmojiFeature.findAutocompletePrefix("::aga");
        assertNotNull(doubleColon);
        assertEquals(1, doubleColon.startIndex());
        assertEquals(":aga", doubleColon.prefix());

        // Adjacent colon after completed emoji: ":cat::aga"
        EmojiFeature.AutocompletePrefix afterEmoji = EmojiFeature.findAutocompletePrefix(":cat::aga");
        assertNotNull(afterEmoji);
        assertEquals(5, afterEmoji.startIndex());
        assertEquals(":aga", afterEmoji.prefix());

        // Adjacent colon in command: "/p ::aga"
        EmojiFeature.AutocompletePrefix inCommand = EmojiFeature.findAutocompletePrefix("/p ::aga");
        assertNotNull(inCommand);
        assertEquals(4, inCommand.startIndex());
        assertEquals(":aga", inCommand.prefix());

        // Double colon with no letters yet: "::"
        EmojiFeature.AutocompletePrefix doubleColonOnly = EmojiFeature.findAutocompletePrefix("::");
        assertNotNull(doubleColonOnly);
        assertEquals(1, doubleColonOnly.startIndex());
        assertEquals(":", doubleColonOnly.prefix());

        // Adjacent colon after completed emoji with no letters yet: ":cat::"
        EmojiFeature.AutocompletePrefix afterEmojiOnly = EmojiFeature.findAutocompletePrefix(":cat::");
        assertNotNull(afterEmojiOnly);
        assertEquals(5, afterEmojiOnly.startIndex());
        assertEquals(":", afterEmojiOnly.prefix());

        // Triple colon: ":::"
        EmojiFeature.AutocompletePrefix tripleColon = EmojiFeature.findAutocompletePrefix(":::");
        assertNotNull(tripleColon);
        assertEquals(2, tripleColon.startIndex());
        assertEquals(":", tripleColon.prefix());
    }

    @Test
    void findAutocompletePrefixHandlesStandardTyping() {
        // Start of text
        EmojiFeature.AutocompletePrefix start = EmojiFeature.findAutocompletePrefix(":ag");
        assertNotNull(start);
        assertEquals(0, start.startIndex());
        assertEquals(":ag", start.prefix());

        // Start with only colon
        EmojiFeature.AutocompletePrefix singleColon = EmojiFeature.findAutocompletePrefix(":");
        assertNotNull(singleColon);
        assertEquals(0, singleColon.startIndex());
        assertEquals(":", singleColon.prefix());

        // Space before colon
        EmojiFeature.AutocompletePrefix afterSpace = EmojiFeature.findAutocompletePrefix("hello :ag");
        assertNotNull(afterSpace);
        assertEquals(6, afterSpace.startIndex());
        assertEquals(":ag", afterSpace.prefix());

        // Inside parenthesis
        EmojiFeature.AutocompletePrefix afterParen = EmojiFeature.findAutocompletePrefix("(:ag");
        assertNotNull(afterParen);
        assertEquals(1, afterParen.startIndex());
        assertEquals(":ag", afterParen.prefix());
    }

    @Test
    void findAutocompletePrefixIgnoresNonEmojiAndClosedEmoji() {
        // Closed emoji should not pop up suggestions
        assertNull(EmojiFeature.findAutocompletePrefix(":cat:"));

        // Namespaced identifier (letter immediately before colon)
        assertNull(EmojiFeature.findAutocompletePrefix("minecraft:stone"));

        // URL containing colons and slashes
        assertNull(EmojiFeature.findAutocompletePrefix("http://website.com"));

        // Text with no colon
        assertNull(EmojiFeature.findAutocompletePrefix("hello world"));
        assertNull(EmojiFeature.findAutocompletePrefix(""));
        assertNull(EmojiFeature.findAutocompletePrefix(null));
    }

    @Test
    void autocompleteWithAdjacentColonFindsMatchingShortcodes() {
        Set<String> custom = Set.of(":aga:", ":agabounce:", ":catwat:");
        Set<String> standard = Set.of(":agree:");

        // Simulate typing "::ag"
        EmojiFeature.AutocompletePrefix prefixInfo = EmojiFeature.findAutocompletePrefix("::ag");
        assertNotNull(prefixInfo);

        List<String> matches = EmojiFeature.matchShortcodes(custom, standard, prefixInfo.prefix());
        assertEquals(List.of(":aga:", ":agabounce:", ":agree:"), matches);
    }
}
