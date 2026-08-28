package info.avicia.avoutils.core.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Base for screens that render a scrollable vertical list of cards. Provides the shared scroll
 * offset, clamping, and row-visibility helpers. Size-agnostic: subclasses own their row heights,
 * capacity/fullness logic, and card rendering.
 */
public abstract class ScrollableListScreen extends Screen {

    protected int scrollOffset = 0;

    protected ScrollableListScreen(Text title) {
        super(title);
    }

    /**
     * Apply one scroll-wheel step and clamp the offset to the content bounds. Returns the new offset.
     */
    protected int applyScroll(int contentHeight, int listTop, double verticalAmount) {
        int maxScroll = Math.max(0, contentHeight - (height - listTop - 10));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (verticalAmount * 20)));
        return scrollOffset;
    }

    /**
     * Whether a row at {@code y} with the given height is at least partially visible in the list
     * viewport bounded by {@code listTop} and {@code listBottom}.
     */
    protected static boolean isRowVisible(int y, int rowHeight, int listTop, int listBottom) {
        return y + rowHeight >= listTop && y <= listBottom;
    }
}
