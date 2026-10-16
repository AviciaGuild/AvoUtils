package info.avicia.avoutils.core.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertical scrolling for a clipped region of a screen, such as a settings list or a modal body that
 * may not fit at high GUI scales.
 *
 * <p>Tracks the visible bounds, the content height, and the scroll offset. Widgets registered with
 * {@link #track} move to their scrolled positions whenever the offset changes. Owners render them
 * with {@link #renderTracked} inside {@link #enableScissor}, and must not route clicks outside
 * {@link #contains} to them, since tracked widgets may be clipped there.
 */
public final class ScrollViewport {

    private static final int SCROLL_STEP = 20;
    private static final int SCROLLBAR_WIDTH = 2;
    private static final int MIN_THUMB_HEIGHT = 12;

    private final List<TrackedWidget> trackedWidgets = new ArrayList<>();

    private int top;
    private int bottom;
    private int contentHeight;
    private int offset;

    private record TrackedWidget(ClickableWidget widget, int baseY) {}

    /**
     * Sets the visible region and the content height (measured from {@code top}), clamping the
     * current offset so the content stays in view.
     */
    public void update(int top, int bottom, int contentHeight) {
        this.top = top;
        this.bottom = Math.max(top, bottom);
        this.contentHeight = Math.max(0, contentHeight);
        setOffset(offset);
    }

    public int getTop() {
        return top;
    }

    public int getBottom() {
        return bottom;
    }

    public int getOffset() {
        return offset;
    }

    public int getMaxOffset() {
        return Math.max(0, contentHeight - (bottom - top));
    }

    /**
     * Whether a point at screen Y {@code y} lies inside the visible region.
     */
    public boolean contains(double y) {
        return y >= top && y < bottom;
    }

    /**
     * Applies one scroll-wheel step. Returns false when the content fits, so callers can let the
     * event fall through.
     */
    public boolean scroll(double verticalAmount) {
        if (getMaxOffset() == 0) {
            return false;
        }
        setOffset(offset - (int) (verticalAmount * SCROLL_STEP));
        return true;
    }

    /**
     * Registers a widget whose current Y is its unscrolled position, and returns it.
     */
    public <T extends ClickableWidget> T track(T widget) {
        trackedWidgets.add(new TrackedWidget(widget, widget.getY()));
        widget.setY(widget.getY() - offset);
        return widget;
    }

    /**
     * Forgets all tracked widgets, e.g. before a screen rebuilds them in {@code init()}.
     */
    public void clearTracked() {
        trackedWidgets.clear();
    }

    /**
     * Clips subsequent drawing to the visible region between {@code left} and {@code right}. Must be
     * paired with {@link DrawContext#disableScissor()}.
     */
    public void enableScissor(DrawContext context, int left, int right) {
        context.enableScissor(left, top, right, bottom);
    }

    /**
     * Renders the tracked widgets; call between {@link #enableScissor} and
     * {@link DrawContext#disableScissor()}. Hover is suppressed while the cursor is outside the
     * visible region, so a clipped half of a widget can't light up the visible half.
     */
    public void renderTracked(DrawContext context, int mouseX, int mouseY, float delta) {
        int hoverY = contains(mouseY) ? mouseY : Integer.MIN_VALUE;
        for (TrackedWidget tracked : trackedWidgets) {
            tracked.widget().render(context, mouseX, hoverY, delta);
        }
    }

    /**
     * Draws a thin scrollbar with its left edge at {@code x}, only when the content overflows.
     */
    public void drawScrollbar(DrawContext context, int x) {
        int maxOffset = getMaxOffset();
        if (maxOffset == 0) {
            return;
        }
        int viewHeight = bottom - top;
        int thumbHeight = Math.min(viewHeight, Math.max(MIN_THUMB_HEIGHT, viewHeight * viewHeight / contentHeight));
        int thumbY = top + (viewHeight - thumbHeight) * offset / maxOffset;
        context.fill(x, top, x + SCROLLBAR_WIDTH, bottom, UiStyle.BORDER_FAINT);
        context.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, UiStyle.ACCENT_BLUE);
    }

    private void setOffset(int value) {
        offset = Math.max(0, Math.min(getMaxOffset(), value));
        for (TrackedWidget tracked : trackedWidgets) {
            tracked.widget().setY(tracked.baseY() - offset);
        }
    }
}

