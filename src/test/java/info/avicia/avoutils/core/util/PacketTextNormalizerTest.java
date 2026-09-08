package info.avicia.avoutils.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketTextNormalizerTest {

    @Test
    void normalizeForParsingHandlesNullAndBlank() {
        assertEquals("", PacketTextNormalizer.normalizeForParsing(null));
        assertEquals("", PacketTextNormalizer.normalizeForParsing(""));
        assertEquals("", PacketTextNormalizer.normalizeForParsing("   \t "));
    }

    @Test
    void normalizeForParsingStripsLegacyColorCodes() {
        assertEquals("Hello World", PacketTextNormalizer.normalizeForParsing("§aHello §bWorld"));
        assertEquals("Hello", PacketTextNormalizer.normalizeForParsing("§x§f§fHello"));
    }

    @Test
    void normalizeForParsingStripsAmpersandFontTags() {
        assertEquals("Hello", PacketTextNormalizer.normalizeForParsing("&{custom_font}Hello"));
    }

    @Test
    void normalizeForParsingStripsAmpersandFormatting() {
        assertEquals("Hello", PacketTextNormalizer.normalizeForParsing("&aHello"));
        assertEquals("Hello", PacketTextNormalizer.normalizeForParsing("&lHello"));
    }

    @Test
    void normalizeForParsingCollapsesWhitespace() {
        assertEquals("a b", PacketTextNormalizer.normalizeForParsing("a   b"));
        assertEquals("a b c", PacketTextNormalizer.normalizeForParsing("a\t b\n c"));
    }

    @Test
    void normalizeForParsingFixesPunctuationSpacing() {
        assertEquals("Hello, world!", PacketTextNormalizer.normalizeForParsing("Hello , world !"));
        assertEquals("(hello)", PacketTextNormalizer.normalizeForParsing("( hello )"));
        assertEquals("a, b, c", PacketTextNormalizer.normalizeForParsing("a ,b , c"));
    }

    @Test
    void normalizeForParsingFixesNumericSlashSpacing() {
        assertEquals("5/7", PacketTextNormalizer.normalizeForParsing("5 / 7"));
        assertEquals("10/2", PacketTextNormalizer.normalizeForParsing("10 / 2"));
    }

    @Test
    void normalizeForParsingDropsPrivateUseAndControlCharacters() {
        assertEquals("a b", PacketTextNormalizer.normalizeForParsing("a\uE040b"));
        assertEquals("a b", PacketTextNormalizer.normalizeForParsing("a\u0001b"));
    }

    @Test
    void normalizeForParsingStripsHexAndCustomBracketTags() {
        assertEquals("avo ignis war dps has been kicked from the party!",
                PacketTextNormalizer.normalizeForParsing("&e&{fr:cp}&{fr:d} &o&<1>avo ignis war dps&r&e has been kicked from the party!"));
        assertEquals("Hello", PacketTextNormalizer.normalizeForParsing("&#ffbb33ff&[1]&<1>Hello"));
    }

    @Test
    void stripColorCodesHandlesNullAndSectionSigns() {
        assertEquals("", PacketTextNormalizer.stripColorCodes(null));
        assertEquals("Hello", PacketTextNormalizer.stripColorCodes("§aHello"));
        assertEquals("Hello World", PacketTextNormalizer.stripColorCodes("Hello §xWorld"));
    }
}
