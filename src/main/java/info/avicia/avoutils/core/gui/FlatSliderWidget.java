package info.avicia.avoutils.core.gui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class FlatSliderWidget extends AbstractWidget {
    private final int min;
    private final int max;
    private int value;
    private final Consumer<Integer> onChanged;
    private boolean dragging = false;

    public FlatSliderWidget(int x, int y, int width, int height, int min, int max, int initialValue, Consumer<Integer> onChanged) {
        super(x, y, width, height, Component.literal(""));
        this.min = min;
        this.max = max;
        this.value = Math.max(min, Math.min(max, initialValue));
        this.onChanged = onChanged;
    }

    public void setValue(int value) {
        this.value = Math.max(min, Math.min(max, value));
    }

    public int getValue() {
        return value;
    }

    // AbstractWidget calls these for left-button events; a click must also land on the active slider,
    // and plays the sound
    @Override
    public void onClick(MouseButtonEvent click, boolean doubleClick) {
        this.dragging = true;
        updateValueFromMouse(click.x());
    }

    @Override
    public void onRelease(MouseButtonEvent click) {
        this.dragging = false;
    }

    @Override
    protected void onDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.dragging) {
            updateValueFromMouse(click.x());
        }
    }

    private void updateValueFromMouse(double mouseX) {
        int x = getX();
        int w = getWidth();
        double ratio = (mouseX - x) / (double) w;
        ratio = Math.max(0, Math.min(1, ratio));
        int newValue = min + (int) Math.round(ratio * (max - min));
        if (newValue != this.value) {
            this.value = newValue;
            if (onChanged != null) {
                onChanged.accept(this.value);
            }
        }
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean dimmed = !this.active;

        boolean hovered = isHovered();

        // Track background
        int trackBg = dimmed ? 0xFF0E0E18 : 0xFF14141E;
        context.fill(x, y + h / 2 - 2, x + w, y + h / 2 + 2, trackBg);
        CompatibilityHelper.drawBorder(context, x, y + h / 2 - 2, w, 4,
                dimmed ? 0x0A8A9CFE : UiStyle.BORDER_FAINT);

        double range = max - min;
        double ratio = range == 0 ? 1.0 : (value - min) / range;
        int knobX = x + (int) (ratio * (w - 8));

        // Fill track
        if (knobX > x) {
            int fillColor = dimmed ? 0xFF444466 : UiStyle.ACCENT_BLUE;
            context.fill(x + 1, y + h / 2 - 1, knobX + 4, y + h / 2 + 1, fillColor);
        }

        // Knob
        int knobColor;
        if (dimmed) {
            knobColor = 0xFF555566;
        } else if (hovered || dragging) {
            knobColor = 0xFFFFFFFF;
        } else {
            knobColor = 0xFFD0D4FF;
        }
        context.fill(knobX, y + h / 2 - 4, knobX + 8, y + h / 2 + 4, knobColor);
        CompatibilityHelper.drawBorder(context, knobX, y + h / 2 - 4, 8, 8,
                dimmed ? 0x15555566 : 0x308A9CFE);
    }

    public boolean keyPressed(KeyEvent keyInput) {
        if (!this.active || !this.visible || !this.isFocused()) return false;

        int step = (max - min) <= 10 ? 1 : 5;
        if (keyInput.isLeft() || keyInput.isDown()) {
            int newValue = Math.max(min, value - step);
            if (newValue != value) {
                value = newValue;
                if (onChanged != null) onChanged.accept(value);
            }
            return true;
        }
        if (keyInput.isRight() || keyInput.isUp()) {
            int newValue = Math.min(max, value + step);
            if (newValue != value) {
                value = newValue;
                if (onChanged != null) onChanged.accept(value);
            }
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        // No-op
    }
}
