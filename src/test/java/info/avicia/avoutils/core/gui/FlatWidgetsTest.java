package info.avicia.avoutils.core.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Flat widgets rely on AbstractWidget's mouse handling. A click that passes its checks also plays a
 * sound, which needs a running game, so these tests call onClick directly for accepted clicks.
 */
class FlatWidgetsTest {

    @Test
    void buttonRunsItsActionOnClick() {
        int[] presses = {0};
        FlatButtonWidget button = new FlatButtonWidget(0, 0, 80, 20, Component.literal("Go"), () -> presses[0]++);

        button.onClick(leftClick(10, 10), false);

        assertEquals(1, presses[0]);
    }

    @Test
    void toggleFlipsAndReportsOnClick() {
        List<Boolean> changes = new ArrayList<>();
        FlatToggleWidget toggle = new FlatToggleWidget(0, 0, 30, 16, false, changes::add);

        toggle.onClick(leftClick(5, 5), false);
        assertTrue(toggle.isChecked());
        toggle.onClick(leftClick(5, 5), false);
        assertFalse(toggle.isChecked());

        assertEquals(List.of(true, false), changes);
    }

    @Test
    void clicksThatMissUseAnotherButtonOrHitADisabledWidgetAreIgnored() {
        FlatToggleWidget toggle = new FlatToggleWidget(10, 10, 30, 16, false, null);

        assertFalse(toggle.mouseClicked(leftClick(5, 5), false), "outside the widget");
        assertFalse(toggle.mouseClicked(click(20, 15, InputConstants.MOUSE_BUTTON_RIGHT), false), "right button");
        toggle.active = false;
        assertFalse(toggle.mouseClicked(leftClick(20, 15), false), "disabled");

        assertFalse(toggle.isChecked());
    }

    @Test
    void sliderFollowsTheMouseFromClickUntilRelease() {
        List<Integer> values = new ArrayList<>();
        FlatSliderWidget slider = new FlatSliderWidget(100, 0, 100, 10, 0, 100, 50, values::add);

        slider.onClick(leftClick(125, 5), false);
        assertEquals(25, slider.getValue());

        assertTrue(slider.mouseDragged(leftClick(190, 5), 65, 0));
        assertEquals(90, slider.getValue());
        slider.mouseDragged(leftClick(400, 5), 210, 0); // past the end
        assertEquals(100, slider.getValue());

        assertTrue(slider.mouseReleased(leftClick(400, 5)));
        slider.mouseDragged(leftClick(110, 5), -290, 0); // no longer dragging
        assertEquals(100, slider.getValue());

        assertEquals(List.of(25, 90, 100), values);
    }

    @Test
    void sliderIgnoresOtherMouseButtons() {
        FlatSliderWidget slider = new FlatSliderWidget(100, 0, 100, 10, 0, 100, 50, null);
        slider.onClick(leftClick(150, 5), false);

        MouseButtonEvent rightDrag = click(170, 5, InputConstants.MOUSE_BUTTON_RIGHT);
        assertFalse(slider.mouseDragged(rightDrag, 20, 0));
        assertFalse(slider.mouseReleased(rightDrag), "a right release doesn't end a left drag");
        assertEquals(50, slider.getValue());

        slider.mouseDragged(leftClick(170, 5), 20, 0);
        assertEquals(70, slider.getValue());
    }

    @Test
    void sliderArrowKeysStepOnlyWhenFocused() {
        FlatSliderWidget slider = new FlatSliderWidget(0, 0, 100, 10, 0, 100, 50, null);
        assertFalse(slider.keyPressed(key(InputConstants.KEY_RIGHT)));

        slider.setFocused(true);
        assertTrue(slider.keyPressed(key(InputConstants.KEY_RIGHT)));
        assertEquals(55, slider.getValue());
        assertTrue(slider.keyPressed(key(InputConstants.KEY_DOWN)));
        assertEquals(50, slider.getValue());
    }

    private static MouseButtonEvent leftClick(double x, double y) {
        return click(x, y, InputConstants.MOUSE_BUTTON_LEFT);
    }

    private static MouseButtonEvent click(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
    }

    private static KeyEvent key(int key) {
        return new KeyEvent(key, 0, 0);
    }
}

