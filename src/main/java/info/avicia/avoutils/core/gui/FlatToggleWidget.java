package info.avicia.avoutils.core.gui;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class FlatToggleWidget extends AbstractWidget {
    private boolean checked;
    private final Consumer<Boolean> onChanged;

    public FlatToggleWidget(int x, int y, int width, int height, boolean initialValue, Consumer<Boolean> onChanged) {
        super(x, y, width, height, Component.literal(""));
        this.checked = initialValue;
        this.onChanged = onChanged;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public boolean isChecked() {
        return checked;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean boolean_arg) {
        if (this.active && this.visible && click.button() == 0) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            double mouseX = click.x();
            double mouseY = click.y();
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                this.checked = !this.checked;
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                if (onChanged != null) {
                    onChanged.accept(this.checked);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        boolean dimmed = !this.active;

        // Draw track
        int trackColor;
        if (dimmed) {
            trackColor = checked ? 0xFF555577 : 0xFF181822;
        } else {
            trackColor = checked ? UiStyle.ACCENT_BLUE : 0xFF222232;
        }
        context.fill(x + 2, y + h / 2 - 3, x + w - 2, y + h / 2 + 3, trackColor);
        CompatibilityHelper.drawBorder(context, x + 2, y + h / 2 - 3, w - 4, 6,
                dimmed ? 0x0A8A9CFE : UiStyle.BORDER_FAINT);

        // Draw knob
        int knobSize = 10;
        int knobY = y + h / 2 - 5;
        int knobX = checked ? x + w - knobSize - 2 : x + 2;
        int knobColor = dimmed ? 0xFF666666 : 0xFFFFFFFF;
        context.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, knobColor);
        CompatibilityHelper.drawBorder(context, knobX, knobY, knobSize, knobSize,
                dimmed ? 0x15666666 : 0x308A9CFE);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        // No-op
    }
}
