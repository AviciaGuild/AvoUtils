package info.avicia.avoutils.features.emojis;

import net.minecraft.client.font.TextRenderer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class EmojiTooltipComponentTest {

    @Test
    void testStandardCustomEmojiScale() {
        // Standard custom emoji: fontWidth = 8.0, height = 8.0
        float scale = EmojiTooltipComponent.calculateScale(32, 8.0f, false);
        assertEquals(4.0f, scale, 0.001f);

        float drawnWidth = 8.0f * scale;
        float drawnHeight = 8.0f * scale;
        assertEquals(32.0f, drawnWidth, 0.001f);
        assertEquals(32.0f, drawnHeight, 0.001f);
    }

    @Test
    void testWideEmojiScaleNeverExceedsSize() {
        // Wide emoji (e.g. fontWidth = 16.0)
        float scale = EmojiTooltipComponent.calculateScale(32, 16.0f, false);
        assertEquals(2.0f, scale, 0.001f);

        float drawnWidth = 16.0f * scale;
        float drawnHeight = 8.0f * scale;
        assertEquals(32.0f, drawnWidth, 0.001f);
        assertTrue(drawnWidth <= 32.0f);
        assertEquals(16.0f, drawnHeight, 0.001f);
        assertTrue(drawnHeight <= 32.0f);
    }

    @Test
    void testNarrowEmojiScaleAndCentering() {
        // Narrow emoji (e.g. fontWidth = 4.0)
        float scale = EmojiTooltipComponent.calculateScale(32, 4.0f, false);
        assertEquals(4.0f, scale, 0.001f);

        float drawnWidth = 4.0f * scale;
        float drawnHeight = 8.0f * scale;
        assertEquals(16.0f, drawnWidth, 0.001f);
        assertEquals(32.0f, drawnHeight, 0.001f);

        // Horizontal center in 32px area has 8px left margin
        float horizontalMargin = (32.0f - drawnWidth) / 2.0f;
        assertEquals(8.0f, horizontalMargin, 0.001f);
    }

    @Test
    void testTwemojiScale() {
        // Twemoji with width 9.0 and height 9.0
        float scale = EmojiTooltipComponent.calculateScale(32, 9.0f, true);
        assertEquals(32.0f / 9.0f, scale, 0.001f);

        float drawnWidth = 9.0f * scale;
        float drawnHeight = 9.0f * scale;
        assertEquals(32.0f, drawnWidth, 0.001f);
        assertEquals(32.0f, drawnHeight, 0.001f);
    }

    @Test
    void testDimensionsAndGetters() {
        EmojiTooltipComponent component = new EmojiTooltipComponent("\uDB80\uDC00", "testEmoji", 32);
        assertEquals("\uDB80\uDC00", component.getEmojiChar());
        assertEquals("testEmoji", component.getEmojiName());
        assertEquals(32, component.getSize());

        TextRenderer textRenderer = Mockito.mock(TextRenderer.class);
        when(textRenderer.getWidth(":testEmoji:")).thenReturn(50);

        assertEquals(50, component.getWidth(textRenderer));
        assertEquals(32 + 7 + textRenderer.fontHeight, component.getHeight(textRenderer));
    }

    @Test
    void testNullEmojiNameHandledGracefully() {
        EmojiTooltipComponent component = new EmojiTooltipComponent("\uDB80\uDC00", null, 32);
        TextRenderer textRenderer = Mockito.mock(TextRenderer.class);
        when(textRenderer.getWidth("")).thenReturn(0);

        assertEquals(32, component.getWidth(textRenderer));
    }
}
