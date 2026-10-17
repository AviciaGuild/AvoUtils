package info.avicia.avoutils.core.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base for screens that render a scrollable vertical list of cards. Provides the shared scroll
 * offset, clamping, and row-visibility helpers. Size-agnostic: subclasses own their row heights,
 * capacity/fullness logic, and card rendering.
 */
public abstract class ScrollableListScreen extends Screen {

    private static final int LIST_BOTTOM_MARGIN = 10;

    protected int scrollOffset = 0;

    protected ScrollableListScreen(Component title) {
        super(title);
    }

    /**
     * Apply one scroll-wheel step and clamp the offset to the content bounds. Returns the new offset.
     */
    protected int applyScroll(int contentHeight, int listTop, double verticalAmount) {
        int maxScroll = Math.max(0, contentHeight - (getListBottom() - listTop));
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

    /**
     * Bottom edge of the list viewport.
     */
    protected int getListBottom() {
        return height - LIST_BOTTOM_MARGIN;
    }

    /**
     * Whether {@code mouseY} is inside the list viewport. Rows partly scrolled under the top bar are
     * clipped there, so they must not react to hover or clicks over the top bar's buttons.
     */
    protected boolean isInListViewport(double mouseY, int listTop) {
        return mouseY >= listTop && mouseY < getListBottom();
    }

    /**
     * Clips drawing to the list viewport so partly scrolled rows don't paint over the top bar. Must
     * be paired with {@link GuiGraphics#disableScissor()}.
     */
    protected void enableListScissor(GuiGraphics context, int listTop) {
        context.enableScissor(0, listTop, width, getListBottom());
    }
}
