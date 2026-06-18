package info.avicia.partyfinder.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class CompatibilityHelper {

    public static void drawTextWithShadow(DrawContext context, TextRenderer textRenderer, Text text, int x, int y, int color) {
        context.drawText(textRenderer, text, x, y, color, true);
    }
}
