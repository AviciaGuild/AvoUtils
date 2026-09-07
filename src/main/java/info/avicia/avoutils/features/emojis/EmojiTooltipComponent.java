package info.avicia.avoutils.features.emojis;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.util.Colors;
import org.joml.Matrix3x2fStack;

/**
 * Custom tooltip component rendering a large emoji preview and shortcode name.
 */
@Environment(EnvType.CLIENT)
public class EmojiTooltipComponent implements TooltipComponent {

    private final String emojiChar;
    private final String emojiName;
    private final int size;

    public EmojiTooltipComponent(String emojiChar, String emojiName, int size) {
        this.emojiChar = emojiChar;
        this.emojiName = emojiName;
        this.size = size;
    }

    public EmojiTooltipComponent(String emojiChar, String emojiName) {
        this(emojiChar, emojiName, 32);
    }

    public String getEmojiChar() {
        return emojiChar;
    }

    public String getEmojiName() {
        return emojiName;
    }

    public int getSize() {
        return size;
    }

    @Override
    public int getWidth(TextRenderer textRenderer) {
        String label = emojiName != null ? ":" + emojiName + ":" : "";
        int textWidth = textRenderer.getWidth(label);
        return Math.max(size, textWidth);
    }

    @Override
    public int getHeight(TextRenderer textRenderer) {
        return size + 7 + textRenderer.fontHeight;
    }

    public static float calculateScale(int size, float fontWidth, boolean isTwemoji) {
        float baseHeight = isTwemoji ? 9.0f : 8.0f;
        return Math.min((float) size / Math.max(1.0f, fontWidth), (float) size / baseHeight);
    }

    @Override
    public void drawItems(TextRenderer textRenderer, int x, int y, int width, int height, DrawContext context) {
        if (emojiChar == null || emojiChar.isEmpty()) {
            return;
        }

        float emojiFontWidth = Math.max(1, textRenderer.getWidth(emojiChar));
        int codePoint = emojiChar.codePointAt(0);
        boolean isTwemoji = codePoint < 0xF0000;
        float baseHeight = isTwemoji ? 9.0f : 8.0f;
        int localYOffset = isTwemoji ? 1 : 0;

        float scale = calculateScale(size, emojiFontWidth, isTwemoji);
        float drawnWidth = emojiFontWidth * scale;
        float drawnHeight = baseHeight * scale;

        float emojiX = x + (width - drawnWidth) / 2.0f;
        float emojiY = y + 2.0f + (size - drawnHeight) / 2.0f;

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(emojiX, emojiY);
            matrices.scale(scale, scale);
            context.drawText(textRenderer, emojiChar, 0, localYOffset, Colors.WHITE, false);
        } finally {
            matrices.popMatrix();
        }

        String label = emojiName != null ? ":" + emojiName + ":" : "";
        int textWidth = textRenderer.getWidth(label);
        int textX = x + Math.max(0, (width - textWidth) / 2);
        int textY = y + size + 5;
        context.drawTextWithShadow(textRenderer, label, textX, textY, 0xFFAAAAAA);
    }
}

