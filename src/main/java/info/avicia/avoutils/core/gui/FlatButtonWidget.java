package info.avicia.avoutils.core.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

public class FlatButtonWidget extends ClickableWidget {
    private final Runnable onPress;
    private boolean selected = false;
    private boolean isDanger = false;
    private boolean borderless = false;

    private Integer selectedBorderColor = null;
    private Integer selectedBgColor = null;
    private Integer selectedTextColor = null;

    public FlatButtonWidget(int x, int y, int width, int height, Text message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setSelectedColors(int border, int bg, int text) {
        this.selectedBorderColor = border;
        this.selectedBgColor = bg;
        this.selectedTextColor = text;
    }

    public void setDanger(boolean danger) {
        this.isDanger = danger;
    }

    public void setBorderless(boolean borderless) {
        this.borderless = borderless;
    }

    @Override
    public boolean mouseClicked(Click click, boolean boolean_arg) {
        if (this.active && this.visible && click.button() == 0) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            double mouseX = click.x();
            double mouseY = click.y();
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                if (onPress != null) {
                    onPress.run();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        
        // Determine hover state
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        if (borderless) {
            // Draw a background highlight on hover
            if (hovered) {
                int hoverBgColor = isDanger ? 0x25FF4D4D : 0x15FFFFFF;
                context.fill(x, y, x + w, y + h, hoverBgColor);
            }
        } else {
            // Background
            int bgColor;
            if (selected) {
                bgColor = (selectedBgColor != null) ? selectedBgColor : 0x258A9CFE;
            } else {
                bgColor = hovered ? 0xF22A2D3C : 0xD5161622;
            }
            context.fill(x, y, x + w, y + h, bgColor);

            // Border color
            int borderColor;
            if (selected) {
                borderColor = (selectedBorderColor != null) ? selectedBorderColor : 0xFF8A9CFE;
            } else if (isDanger) {
                borderColor = hovered ? 0xFFFF4D4D : 0xAAFF4D4D;
            } else {
                borderColor = hovered ? 0xFF8A9CFE : 0x1A8A9CFE;
            }

            // Draw flat borders
            CompatibilityHelper.drawBorder(context, x, y, w, h, borderColor);
        }

        // Text formatting and drawing
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int textW = textRenderer.getWidth(getMessage());
        int textX = x + (w - textW) / 2;
        int textY = y + (h - 8) / 2;

        int textColor;
        if (selected) {
            textColor = (selectedTextColor != null) ? selectedTextColor : 0xFF8A9CFE;
        } else if (isDanger) {
            textColor = hovered ? 0xFFFF4D4D : 0xFFAA4444;
        } else {
            textColor = hovered ? 0xFFFFFFFF : 0xFFA0A5B5;
        }

        CompatibilityHelper.drawTextWithShadow(context, textRenderer, getMessage(), textX, textY, textColor);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // No-op
    }
}
