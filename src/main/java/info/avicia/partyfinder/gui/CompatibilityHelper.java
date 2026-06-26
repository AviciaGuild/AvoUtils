package info.avicia.partyfinder.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class CompatibilityHelper {

    public static void drawTextWithShadow(DrawContext context, TextRenderer textRenderer, Text text, int x, int y, int color) {
        context.drawText(textRenderer, text, x, y, color, true);
    }

    public static void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color); // top
        context.fill(x, y + h - 1, x + w, y + h, color); // bottom
        context.fill(x, y, x + 1, y + h, color); // left
        context.fill(x + w - 1, y, x + w, y + h, color); // right
    }

    public static void drawModalFrame(DrawContext context, int x, int y, int w, int h) {
        // Modal drop shadow
        context.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x7F000000);

        // Modal background
        context.fill(x, y, x + w, y + h, 0xF812121C);

        // Outline border
        drawBorder(context, x, y, w, h, 0xFF8A9CFE);

        // Header stripe background & separator line
        context.fill(x + 1, y + 1, x + w - 1, y + 24, 0x308A9CFE);
        context.fill(x + 1, y + 24, x + w - 1, y + 25, 0xFF8A9CFE);
    }
}
