package info.avicia.avoutils.core.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
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

        // Detect mouse hover state
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        // GLFW mouse state check for dragging fallback
        if (this.dragging) {
            long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
            boolean isPressed = GLFW.glfwGetMouseButton(windowHandle, 0) == GLFW.GLFW_PRESS;
            if (isPressed) {
                updateValueFromMouse(mouseX);
            } else {
                this.dragging = false;
            }
        }

        // Draw track background
        context.fill(x, y + h / 2 - 2, x + w, y + h / 2 + 2, 0xFF14141E);
        CompatibilityHelper.drawBorder(context, x, y + h / 2 - 2, w, 4, 0x1A8A9CFE);

        // Calculate progress knob position
        double range = max - min;
        double ratio = range == 0 ? 0 : (value - min) / range;
        int knobX = x + (int) (ratio * (w - 8));

        // Draw fill track
        if (knobX > x) {
            context.fill(x + 1, y + h / 2 - 1, knobX + 4, y + h / 2 + 1, 0xFF8A9CFE);
        }

        // Draw knob
        int knobColor = (hovered || dragging) ? 0xFFFFFFFF : 0xFFD0D4FF;
        context.fill(knobX, y + h / 2 - 4, knobX + 8, y + h / 2 + 4, knobColor);
        CompatibilityHelper.drawBorder(context, knobX, y + h / 2 - 4, 8, 8, 0x308A9CFE);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // No-op
    }
}
