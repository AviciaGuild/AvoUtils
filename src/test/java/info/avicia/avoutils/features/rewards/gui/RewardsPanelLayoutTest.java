package info.avicia.avoutils.features.rewards.gui;

import info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout.Position;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardsPanelLayoutTest {

    @Test
    void sitsLeftOfTheMenuAtMostMaxWidthWide() {
        RewardsPanelLayout layout = new RewardsPanelLayout(640, 360, 232, 69, 222);

        assertEquals(RewardsPanelLayout.MAX_WIDTH, layout.width());
        assertEquals(232 - RewardsPanelLayout.MARGIN, layout.right());
        assertEquals(layout.right() - layout.width(), layout.x());
        assertFalse(layout.showsHint());
    }

    @Test
    void shrinksToTheRoomLeftOfTheMenuAndNeverCoversIt() {
        for (int leftPos = 0; leftPos < 400; leftPos++) {
            RewardsPanelLayout layout = new RewardsPanelLayout(800, 400, leftPos, 50, 222);
            if (layout.showsHint()) continue;
            assertTrue(layout.right() < leftPos, "leftPos " + leftPos);
            assertTrue(layout.x() >= RewardsPanelLayout.MARGIN, "leftPos " + leftPos);
        }
    }

    @Test
    void showsOnlyTheHintWhenNarrowerThanTheMinimum() {
        int minimumLeftPos = RewardsPanelLayout.MIN_WIDTH + 2 * RewardsPanelLayout.MARGIN;

        assertFalse(new RewardsPanelLayout(400, 300, minimumLeftPos, 40, 222).showsHint());
        assertTrue(new RewardsPanelLayout(400, 300, minimumLeftPos - 1, 40, 222).showsHint());
    }

    @Test
    void spansTheMenuHeightWithinTheScreen() {
        RewardsPanelLayout layout = new RewardsPanelLayout(640, 360, 232, 69, 222);
        assertEquals(69, layout.y());
        assertEquals(69 + 222, layout.bottom());

        RewardsPanelLayout tall = new RewardsPanelLayout(640, 240, 232, 0, 300);
        assertEquals(RewardsPanelLayout.MARGIN, tall.y());
        assertEquals(240 - RewardsPanelLayout.MARGIN, tall.bottom());

        RewardsPanelLayout shortMenu = new RewardsPanelLayout(640, 360, 232, 100, 60);
        assertEquals(100 + RewardsPanelLayout.MIN_HEIGHT, shortMenu.bottom());
    }

    @Test
    void theStatusPushesTheFooterUpAndTheListEndsAboveIt() {
        RewardsPanelLayout layout = new RewardsPanelLayout(640, 360, 232, 69, 222);

        assertEquals(layout.bottom() - RewardsPanelLayout.PADDING - RewardsPanelLayout.BUTTON_H, layout.footerTop(0));
        assertEquals(layout.footerTop(0) - 2 * RewardsPanelLayout.LINE_H - RewardsPanelLayout.ROW_GAP,
                layout.footerTop(2));
        assertTrue(layout.listBottom(2) < layout.footerTop(2));
        assertEquals(layout.footerTop(2) + RewardsPanelLayout.BUTTON_H + RewardsPanelLayout.ROW_GAP,
                layout.statusTop(2));
        assertEquals(layout.bottom() - RewardsPanelLayout.PADDING,
                layout.statusTop(2) + 2 * RewardsPanelLayout.LINE_H);
    }

    @Test
    void containsOnlyItsOwnArea() {
        RewardsPanelLayout layout = new RewardsPanelLayout(640, 360, 232, 69, 222);

        assertTrue(layout.contains(layout.x(), layout.y()));
        assertFalse(layout.contains(layout.right(), layout.y()));
        assertFalse(layout.contains(232, 100));
        assertFalse(layout.contains(layout.x(), layout.bottom()));
    }

    @Test
    void flowsPillsIntoRows() {
        assertEquals(List.of(new Position(0, 0), new Position(53, 0), new Position(0, 1)),
                RewardsPanelLayout.flow(List.of(50, 60, 70), 150, 3));
        assertEquals(List.of(new Position(0, 0), new Position(0, 1)),
                RewardsPanelLayout.flow(List.of(200, 10), 150, 3));
    }
}
