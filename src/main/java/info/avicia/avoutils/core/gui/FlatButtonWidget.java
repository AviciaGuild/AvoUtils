package info.avicia.avoutils.core.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class FlatButtonWidget extends AbstractWidget {
    private final Runnable onPress;
    private boolean selected = false;
    private boolean isDanger = false;
    private boolean borderless = false;

    private Integer selectedBorderColor = null;
    private Integer selectedBgColor = null;
    private Integer selectedTextColor = null;
    private UiIcons.Icon icon = null;

    public FlatButtonWidget(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public FlatButtonWidget withIcon(UiIcons.Icon icon) {
        this.icon = icon;
        return this;
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

    // AbstractWidget.mouseClicked checks it's active, the left button and the bounds, and plays the sound
    @Override
    public void onClick(MouseButtonEvent click, boolean doubleClick) {
        if (onPress != null) {
            onPress.run();
        }
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        
        // Determine hover state
        boolean hovered = active && isHovered();

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
                bgColor = hovered ? 0xF22A2D3C : UiStyle.CARD_BACKGROUND;
            }
            context.fill(x, y, x + w, y + h, bgColor);

            // Border color
            int borderColor;
            if (selected) {
                borderColor = (selectedBorderColor != null) ? selectedBorderColor : UiStyle.ACCENT_BLUE;
            } else if (isDanger) {
                borderColor = hovered ? UiStyle.ACCENT_RED : 0xAAFF4D4D;
            } else {
                borderColor = hovered ? UiStyle.ACCENT_BLUE : UiStyle.BORDER_FAINT;
            }

            // Draw flat borders
            CompatibilityHelper.drawBorder(context, x, y, w, h, borderColor);
        }

        // Text formatting and drawing
        Font textRenderer = Minecraft.getInstance().font;
        int textW = textRenderer.width(getMessage());
        int textX = x + (w - textW) / 2;
        int textY = y + (h - 8) / 2;

        int textColor;
        if (selected) {
            textColor = (selectedTextColor != null) ? selectedTextColor : UiStyle.ACCENT_BLUE;
        } else if (isDanger) {
            textColor = hovered ? UiStyle.ACCENT_RED : 0xFFAA4444;
        } else {
            textColor = hovered ? 0xFFFFFFFF : 0xFFA0A5B5;
        }

        if (icon != null) {
            if (getMessage().getString().isEmpty()) {
                icon.draw(context, x, y, w, h, textColor);
            } else {
                int iconW = icon.widthPx();
                int gap = 4;
                int startX = x + (w - (iconW + gap + textW)) / 2;
                icon.draw(context, startX, textY - 1, iconW, 9, textColor);
                CompatibilityHelper.drawTextWithShadow(context, textRenderer, getMessage(),
                        startX + iconW + gap, textY, textColor);
            }
        } else {
            CompatibilityHelper.drawTextWithShadow(context, textRenderer, getMessage(), textX, textY, textColor);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        // No-op
    }
}
