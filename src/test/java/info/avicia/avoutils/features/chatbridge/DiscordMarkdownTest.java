package info.avicia.avoutils.features.chatbridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordMarkdownTest {

    @Test
    void nullBecomesEmptyString() {
        assertEquals("", DiscordMarkdown.escapeUsername(null));
    }

    @Test
    void underscoresAreEscaped() {
        assertEquals("Steve\\_Bob", DiscordMarkdown.escapeUsername("Steve_Bob"));
        assertEquals("\\_leading", DiscordMarkdown.escapeUsername("_leading"));
    }

    @Test
    void plainTextIsUnchanged() {
        assertEquals("Steve", DiscordMarkdown.escapeUsername("Steve"));
    }
}
