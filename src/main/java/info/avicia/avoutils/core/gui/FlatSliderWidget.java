package info.avicia.avoutils.core.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class FlatSliderWidget extends ClickableWidget {
    private final int min;
    private final int max;
    private int value;
    private final Consumer<Integer> onChanged;
    private boolean dragging = false;

    public FlatSliderWidget(int x, int y, int width, int height, int min, int max, int initialValue, Consumer<Integer> onChanged) {
        super(x, y, width, height, Text.literal(""));
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
                this.dragging = true;
                updateValueFromMouse(mouseX);
                this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && this.dragging) {
            this.dragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (click.button() == 0 && this.dragging) {
            updateValueFromMouse(click.x());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
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
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean dimmed = !this.active;

        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        if (this.dragging && !dimmed) {
            long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
            boolean isPressed = GLFW.glfwGetMouseButton(windowHandle, 0) == GLFW.GLFW_PRESS;
            if (isPressed) {
                updateValueFromMouse(mouseX);
            } else {
                this.dragging = false;
            }
        }

        // Track background
        int trackBg = dimmed ? 0xFF0E0E18 : 0xFF14141E;
        context.fill(x, y + h / 2 - 2, x + w, y + h / 2 + 2, trackBg);
        CompatibilityHelper.drawBorder(context, x, y + h / 2 - 2, w, 4,
                dimmed ? 0x0A8A9CFE : 0x1A8A9CFE);

        double range = max - min;
        double ratio = range == 0 ? 0 : (value - min) / range;
        int knobX = x + (int) (ratio * (w - 8));

        // Fill track
        if (knobX > x) {
            int fillColor = dimmed ? 0xFF444466 : 0xFF8A9CFE;
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

    public boolean keyPressed(KeyInput keyInput) {
        if (!this.active || !this.visible || !this.isFocused()) return false;

        int step = (max - min) <= 10 ? 1 : 5;
        int key = keyInput.key();
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_DOWN) {
            int newValue = Math.max(min, value - step);
            if (newValue != value) {
                value = newValue;
                if (onChanged != null) onChanged.accept(value);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP) {
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
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // No-op
    }
}
