package info.avicia.avoutils.core.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class CompatibilityHelper {

    public static void drawTextWithShadow(DrawContext context, TextRenderer textRenderer, Text text, int x, int y, int color) {
        context.drawText(textRenderer, text, x, y, color, true);
    }

    public static void drawScreenTitle(DrawContext context, TextRenderer textRenderer, int width, String title) {
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(title), width / 2, 12, UiStyle.TEXT_WHITE);
    }

    public static void drawCenteredMessage(DrawContext context, TextRenderer textRenderer, int width, int height, String message) {
        Text text = Text.literal(message);
        int textWidth = textRenderer.getWidth(text);
        drawTextWithShadow(context, textRenderer, text, (width - textWidth) / 2, height / 2, UiStyle.TEXT_WHITE);
    }

    public static void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color); // top
        context.fill(x, y + h - 1, x + w, y + h, color); // bottom
        context.fill(x, y, x + 1, y + h, color); // left
        context.fill(x + w - 1, y, x + w, y + h, color); // right
    }

    public static void drawModalFrame(DrawContext context, int x, int y, int w, int h) {
        // Modal drop shadow
        context.fill(x + 3, y + 3, x + w + 3, y + h + 3, UiStyle.MODAL_SHADOW);

        // Modal background
        context.fill(x, y, x + w, y + h, UiStyle.MODAL_BACKGROUND);

        // Header background stripe
        context.fill(x + 1, y + 1, x + w - 1, y + 24, UiStyle.MODAL_HEADER);

        // Outline border
        drawBorder(context, x, y, w, h, UiStyle.MODAL_OUTLINE);

        // Header separator line
        drawBorder(context, x + 1, y + 24, w - 2, 1, UiStyle.MODAL_SEPARATOR);
    }
}
