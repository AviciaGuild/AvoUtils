package info.avicia.avoutils.core.gui;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks shared by the screen layout tests.
 */
public final class LayoutAssertions {

    private LayoutAssertions() {
    }

    /**
     * Asserts that a box spanning unscrolled Y {@code boxTop} to {@code boxBottom} can be scrolled
     * fully into a viewport with the mouse wheel, trying one notch at a time from the top.
     */
    public static void assertScrollsIntoView(String what, int boxTop, int boxBottom,
                                             int viewTop, int viewBottom, int contentHeight) {
        ScrollViewport viewport = new ScrollViewport();
        viewport.update(viewTop, viewBottom, contentHeight);
        int previous;
        do {
            int offset = viewport.getOffset();
            if (boxTop - offset >= viewTop && boxBottom - offset <= viewBottom) {
                return;
            }
            previous = offset;
            viewport.scroll(-1); // one notch down
        } while (viewport.getOffset() != previous);

        fail(what + " (Y " + boxTop + " to " + boxBottom + ") can't be scrolled fully into the viewport ("
                + viewTop + " to " + viewBottom + ", content height " + contentHeight + ")");
    }
}

