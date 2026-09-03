package info.avicia.avoutils.features.emojis;

import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmojiTooltipHelperTest {

    @Test
    void createAndParseEmojiHover() {
        HoverEvent hover = EmojiTooltipHelper.createEmojiHover("\uF801", "catwat");
        assertNotNull(hover);

        EmojiTooltipHelper.EmojiHoverData data = EmojiTooltipHelper.parseEmojiHover(hover);
        assertNotNull(data);
        assertEquals("\uF801", data.replacement());
        assertEquals("catwat", data.emojiName());
    }

    @Test
    void parseEmojiHoverHandlesMultiCharPua() {
        HoverEvent hover = EmojiTooltipHelper.createEmojiHover("\uE200\uE201", "heart");
        EmojiTooltipHelper.EmojiHoverData data = EmojiTooltipHelper.parseEmojiHover(hover);
        assertNotNull(data);
        assertEquals("\uE200\uE201", data.replacement());
        assertEquals("heart", data.emojiName());
    }

    @Test
    void parseEmojiHoverHandlesPlane15SurrogatePair() {
        String plane15Char = Character.toString(0xF0000);
        HoverEvent hover = EmojiTooltipHelper.createEmojiHover(plane15Char, "aga");
        EmojiTooltipHelper.EmojiHoverData data = EmojiTooltipHelper.parseEmojiHover(hover);
        assertNotNull(data);
        assertEquals(plane15Char, data.replacement());
        assertEquals("aga", data.emojiName());
    }

    @Test
    void parseEmojiHoverReturnsNullForNullEvent() {
        assertNull(EmojiTooltipHelper.parseEmojiHover(null));
    }

    @Test
    void parseEmojiHoverReturnsNullForNonEmojiText() {
        HoverEvent hover = new HoverEvent.ShowText(Text.literal("Normal text tooltip"));
        assertNull(EmojiTooltipHelper.parseEmojiHover(hover));
    }

    @Test
    void parseEmojiHoverReturnsNullForNonShowTextEvent() {
        HoverEvent hover = () -> HoverEvent.Action.SHOW_ITEM;
        assertNull(EmojiTooltipHelper.parseEmojiHover(hover));
    }

    @Test
    void parseEmojiHoverReturnsNullForMalformedPrefix() {
        HoverEvent missingColon = new HoverEvent.ShowText(Text.literal(EmojiTooltipHelper.EMOJI_HOVER_PREFIX + "nocolon"));
        assertNull(EmojiTooltipHelper.parseEmojiHover(missingColon));

        HoverEvent trailingColon = new HoverEvent.ShowText(Text.literal(EmojiTooltipHelper.EMOJI_HOVER_PREFIX + "onlyreplacement:"));
        assertNull(EmojiTooltipHelper.parseEmojiHover(trailingColon));

        HoverEvent leadingColon = new HoverEvent.ShowText(Text.literal(EmojiTooltipHelper.EMOJI_HOVER_PREFIX + ":noname"));
        assertNull(EmojiTooltipHelper.parseEmojiHover(leadingColon));
    }
}
