package info.avicia.avoutils.core.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class CompatibilityHelper {

    public static void drawTextWithShadow(GuiGraphics context, Font textRenderer, Component text, int x, int y, int color) {
        context.drawString(textRenderer, text, x, y, color, true);
    }

    public static void drawScreenTitle(GuiGraphics context, Font textRenderer, int width, String title) {
        context.drawCenteredString(textRenderer, Component.literal(title), width / 2, 12, UiStyle.TEXT_WHITE);
    }

    public static void drawCenteredMessage(GuiGraphics context, Font textRenderer, int width, int height, String message) {
        Component text = Component.literal(message);
        int textWidth = textRenderer.width(text);
        drawTextWithShadow(context, textRenderer, text, (width - textWidth) / 2, height / 2, UiStyle.TEXT_WHITE);
    }

    public static void drawBorder(GuiGraphics context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color); // top
        context.fill(x, y + h - 1, x + w, y + h, color); // bottom
        context.fill(x, y, x + 1, y + h, color); // left
        context.fill(x + w - 1, y, x + w, y + h, color); // right
    }

    public static void drawModalFrame(GuiGraphics context, int x, int y, int w, int h) {
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
