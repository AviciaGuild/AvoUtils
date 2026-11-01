package info.avicia.avoutils.features.rewards.gui;

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
    void theButtonsStayAtTheBottomWhileTheStatusTakesRoomFromTheList() {
        RewardsPanelLayout layout = new RewardsPanelLayout(640, 360, 232, 69, 222);
        int buttonsBottom = layout.bottom() - RewardsPanelLayout.BORDER - RewardsPanelLayout.STATUS_GAP;

        assertEquals(buttonsBottom - RewardsPanelLayout.BUTTON_H, layout.buttonsTop(1));
        assertEquals(buttonsBottom - 2 * RewardsPanelLayout.BUTTON_H - RewardsPanelLayout.ROW_GAP,
                layout.buttonsTop(2));
        assertEquals(layout.buttonsTop(1), layout.statusTop(0, false, 1));
        assertEquals(layout.buttonsTop(1) - RewardsPanelLayout.STATUS_GAP - 2 * RewardsPanelLayout.LINE_H,
                layout.statusTop(2, false, 1));
        assertEquals(layout.statusTop(2, false, 1) - RewardsPanelLayout.STATUS_GAP - 1,
                layout.listBottom(2, false, 1));
        assertEquals(layout.buttonsTop(1) - RewardsPanelLayout.STATUS_GAP - 1, layout.listBottom(0, false, 1));
        assertTrue(layout.rowsTop() < layout.listBottom(3, true, 2));
    }

    @Test
    void theProgressBarGoesUnderTheStatus() {
        RewardsPanelLayout layout = new RewardsPanelLayout(640, 360, 232, 69, 222);

        assertEquals(layout.statusTop(1, false, 1) - RewardsPanelLayout.BAR_H - RewardsPanelLayout.BAR_GAP,
                layout.statusTop(1, true, 1));
    }

    @Test
    void theTableStartsUnderTheHeaderAndTheRowsUnderTheTotalsSeparator() {
        RewardsPanelLayout layout = new RewardsPanelLayout(640, 360, 232, 69, 222);

        assertEquals(layout.y() + RewardsPanelLayout.HEADER_H, layout.listTop());
        assertEquals(layout.listTop() + RewardsPanelLayout.TABLE_TOP_GAP, layout.headTop());
        assertEquals(layout.headTop() + RewardsPanelLayout.TABLE_HEAD_H, layout.totalsTop());
        assertEquals(layout.totalsTop() + RewardsPanelLayout.TOTALS_H + 1, layout.totalsSeparatorY());
        assertEquals(layout.totalsSeparatorY() + 1, layout.rowsTop());
    }

    @Test
    void contentSitsInsideSymmetricBorderAndPadding() {
        RewardsPanelLayout layout = new RewardsPanelLayout(640, 360, 232, 69, 222);

        assertEquals(layout.x() + RewardsPanelLayout.BORDER + RewardsPanelLayout.PADDING, layout.contentLeft());
        assertEquals(layout.contentLeft(), layout.tableLeft());
        assertEquals(layout.right() - RewardsPanelLayout.BORDER - RewardsPanelLayout.PADDING,
                layout.tableRight());
        assertEquals(layout.tableRight() - layout.tableLeft(), layout.contentWidth());
    }

    @Test
    void theRewardColumnsSitOnTheRightAndTheNamesGetTheRest() {
        RewardsPanelLayout layout = new RewardsPanelLayout(640, 360, 232, 69, 222);
        int step = RewardsPanelLayout.AUTO_COL_W + RewardsPanelLayout.COLUMN_GAP;

        assertEquals(layout.tableRight(), layout.columnRight(2));
        assertEquals(layout.columnRight(2) - step, layout.columnRight(1));
        assertEquals(layout.columnRight(1) - step, layout.columnRight(0));
        for (int column = 1; column <= 2; column++) {
            assertEquals(layout.columnRight(column) - RewardsPanelLayout.PLAY_SIZE, layout.playX(column));
            assertEquals(layout.playX(column) - RewardsPanelLayout.PLAY_GAP, layout.valueRight(column));
        }
        assertEquals(layout.columnRight(0), layout.valueRight(0));
        assertEquals(layout.columnRight(0) - RewardsPanelLayout.LE_COL_W - RewardsPanelLayout.COLUMN_GAP,
                layout.nameRight());
        assertEquals(76, layout.nameRight() - layout.tableLeft());
    }

    @Test
    void namesKeepRoomEvenInTheNarrowestPanel() {
        int leftPos = RewardsPanelLayout.MIN_WIDTH + 2 * RewardsPanelLayout.MARGIN;
        RewardsPanelLayout layout = new RewardsPanelLayout(400, 300, leftPos, 40, 222);

        assertFalse(layout.showsHint());
        assertEquals(46, layout.nameRight() - layout.tableLeft());
    }

    @Test
    void containsOnlyItsOwnArea() {
        RewardsPanelLayout layout = new RewardsPanelLayout(640, 360, 232, 69, 222);

        assertTrue(layout.contains(layout.x(), layout.y()));
        assertFalse(layout.contains(layout.right(), layout.y()));
        assertFalse(layout.contains(232, 100));
        assertFalse(layout.contains(layout.x(), layout.bottom()));
    }
}
