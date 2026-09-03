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
        int textWidth = textRenderer.getWidth(":" + emojiName + ":");
        return Math.max(size, textWidth);
    }

    @Override
    public int getHeight(TextRenderer textRenderer) {
        return size + 4 + textRenderer.fontHeight;
    }

    @Override
    public void drawItems(TextRenderer textRenderer, int x, int y, int width, int height, DrawContext context) {
        int emojiX = x + Math.max(0, (width - size) / 2);
        int emojiY = y + 1;
        float scale = (float) size / 8.0f;

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(emojiX, emojiY);
        matrices.scale(scale, scale);
        context.drawText(textRenderer, emojiChar, 0, 0, Colors.WHITE, false);
        matrices.popMatrix();

        String label = ":" + emojiName + ":";
        int textWidth = textRenderer.getWidth(label);
        int textX = x + Math.max(0, (width - textWidth) / 2);
        int textY = y + size + 4;
        context.drawTextWithShadow(textRenderer, label, textX, textY, 0xFFAAAAAA);
    }
}

