package info.avicia.avoutils.core.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrollViewportTest {

    @Test
    void contentThatFitsDoesNotScroll() {
        ScrollViewport viewport = new ScrollViewport();
        viewport.update(30, 230, 150);

        assertEquals(0, viewport.getMaxOffset());
        assertFalse(viewport.scroll(-1));
        assertEquals(0, viewport.getOffset());
    }

    @Test
    void scrollingClampsToContentBounds() {
        ScrollViewport viewport = new ScrollViewport();
        viewport.update(30, 230, 300); // 200px visible, 300px of content

        assertEquals(100, viewport.getMaxOffset());
        assertTrue(viewport.scroll(-1)); // one wheel step down
        assertEquals(20, viewport.getOffset());

        viewport.scroll(-10);
        assertEquals(100, viewport.getOffset());

        viewport.scroll(10);
        assertEquals(0, viewport.getOffset());
    }

    @Test
    void resizingClampsTheOffset() {
        ScrollViewport viewport = new ScrollViewport();
        viewport.update(30, 230, 300);
        viewport.scroll(-10);
        assertEquals(100, viewport.getOffset());

        viewport.update(30, 280, 300); // taller window
        assertEquals(50, viewport.getOffset());

        viewport.update(30, 280, 200); // content now fits
        assertEquals(0, viewport.getOffset());
    }

    @Test
    void containsIncludesTopAndExcludesBottom() {
        ScrollViewport viewport = new ScrollViewport();
        viewport.update(30, 230, 300);

        assertFalse(viewport.contains(29.9));
        assertTrue(viewport.contains(30));
        assertTrue(viewport.contains(229.9));
        assertFalse(viewport.contains(230));
    }

    @Test
    void invertedBoundsCollapseToAnEmptyViewport() {
        ScrollViewport viewport = new ScrollViewport();
        viewport.update(100, 40, 50);

        assertEquals(100, viewport.getBottom());
        assertFalse(viewport.contains(100));
        assertEquals(50, viewport.getMaxOffset());
    }

    @Test
    void trackedWidgetsFollowTheOffset() {
        ScrollViewport viewport = new ScrollViewport();
        FlatToggleWidget toggle = viewport.track(new FlatToggleWidget(10, 150, 30, 16, false, null));
        viewport.update(30, 230, 300);
        assertEquals(150, toggle.getY());

        viewport.scroll(-2); // 40px down
        assertEquals(110, toggle.getY());

        viewport.update(30, 230, 220); // max offset shrinks to 20
        assertEquals(130, toggle.getY());
    }

    @Test
    void widgetsTrackedWhileScrolledStartAtTheCurrentOffset() {
        ScrollViewport viewport = new ScrollViewport();
        viewport.update(30, 230, 300);
        viewport.scroll(-3); // 60px down, e.g. a screen re-init while scrolled

        FlatToggleWidget toggle = viewport.track(new FlatToggleWidget(10, 150, 30, 16, false, null));
        assertEquals(90, toggle.getY());
    }

    @Test
    void clearedWidgetsStopMoving() {
        ScrollViewport viewport = new ScrollViewport();
        viewport.update(30, 230, 300);
        FlatToggleWidget toggle = viewport.track(new FlatToggleWidget(10, 150, 30, 16, false, null));

        viewport.clearTracked();
        viewport.scroll(-1);
        assertEquals(150, toggle.getY());
    }
}

