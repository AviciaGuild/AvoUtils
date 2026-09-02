package info.avicia.avoutils.core.util;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WynnPillUtilTest {

    @Test
    void createBuildsPillStructure() {
        Text pill = WynnPillUtil.create("a", Formatting.AQUA, Formatting.BLACK);
        String text = pill.getString();

        assertNotNull(text);
        assertTrue(text.startsWith("\uE010\u2064")); // left corner
        assertTrue(text.endsWith("\uE011\u2064")); // right corner
        assertTrue(text.contains("\uE00F")); // background back
        assertTrue(text.contains("\uE012\uE040")); // background front + glyph for 'a'
    }

    @Test
    void createLowercasesLettersAndMapsDigits() {
        String text = WynnPillUtil.create("A1", Formatting.AQUA, Formatting.BLACK).getString();

        assertTrue(text.contains("\uE040")); // 'a'
        assertTrue(text.contains("\uE061")); // '1'
    }

    @Test
    void createKeepsUnknownCharactersAsIs() {
        String text = WynnPillUtil.create("!", Formatting.AQUA, Formatting.BLACK).getString();
        assertTrue(text.contains("!"));
    }

    @Test
    void createPrefixedPillAppendsArrows() {
        String text = WynnPillUtil.createPrefixedPill("X", false).getString();

        assertTrue(text.startsWith("\uE010\u2064"));
        assertTrue(text.endsWith(" \u203A\u203A "));
    }

    @Test
    void createPrefixedPillSupportsErrorVariant() {
        String text = WynnPillUtil.createPrefixedPill("X", true).getString();
        assertTrue(text.endsWith(" \u203A\u203A "));
    }
}
