package info.avicia.avoutils.features.partyfinder.gui;

import info.avicia.avoutils.core.gui.LayoutAssertions;
import info.avicia.avoutils.features.partyfinder.gui.CreatePartyModalLayout.Row;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatePartyModalLayoutTest {

    // Smallest GUI Minecraft picks (4:3 at Auto), Auto on 1440p (also 720p and 4K), and Auto on 1080p
    private static final int[][] SMALL_SCREENS = {{320, 240}, {426, 240}, {480, 270}};
    // GUI scale 2 on 1080p
    private static final int TALL_W = 960;
    private static final int TALL_H = 540;
    private static final int TEXT_HEIGHT = 9;
    // render() draws status lines 10px apart
    private static final int STATUS_LINE_SPACING = 10;
    // Longest status the body must stay usable with, e.g. a wrapped network error
    private static final int MAX_STATUS_LINES = 3;

    @Test
    void tallScreenKeepsTheOriginalPositions() {
        CreatePartyModalLayout layout = new CreatePartyModalLayout(TALL_W, TALL_H, false);
        assertEquals(320, layout.modalW());
        assertEquals(334, layout.modalH());
        assertEquals(320, layout.modalX());
        assertEquals(103, layout.modalY());

        int y = layout.modalY();
        assertEquals(List.of(y + 38, y + 76, y + 114, y + 152, y + 202, y + 240), rowTops(layout));
        assertEquals(layout.modalX() + 304, layout.closeButtonX());
        assertEquals(y + 6, layout.closeButtonY());
        assertEquals(y + 8, layout.titleY());
        assertEquals(y + 25, layout.bodyTop());
        assertEquals(y + 272, layout.bodyContentBottom());
        assertEquals(y + 302, layout.bodyBottom(0));
        assertEquals(y + 284, layout.statusTop(2));
        assertEquals(y + 282, layout.bodyBottom(2));
        assertEquals(y + 308, layout.saveY());
    }

    @Test
    void editingDropsTheReservedSlotsRow() {
        CreatePartyModalLayout layout = new CreatePartyModalLayout(TALL_W, TALL_H, true);
        assertEquals(296, layout.modalH());
        assertEquals(122, layout.modalY());

        int y = layout.modalY();
        assertEquals(List.of(Row.ACTIVITY, Row.ROLE, Row.REGION, Row.NOTE, Row.PING), layout.rows());
        assertEquals(List.of(y + 38, y + 76, y + 114, y + 152, y + 202), rowTops(layout));
        assertEquals(y + 234, layout.bodyContentBottom());
        assertEquals(y + 270, layout.saveY());
        assertThrows(IllegalArgumentException.class, () -> layout.rowTop(Row.RESERVED));
    }

    @Test
    void smallScreensShrinkTheModal() {
        CreatePartyModalLayout layout = new CreatePartyModalLayout(426, 240, false);
        assertEquals(320, layout.modalW());
        assertEquals(200, layout.modalH());
        assertEquals(53, layout.modalX());
        assertEquals(20, layout.modalY());

        CreatePartyModalLayout narrow = new CreatePartyModalLayout(320, 240, false);
        assertEquals(280, narrow.modalW());
        assertEquals(20, narrow.modalX());
    }

    @Test
    void headerAndSaveButtonFitOnSmallScreens() {
        for (CreatePartyModalLayout layout : smallScreenLayouts()) {
            String at = describe(layout);
            int modalRight = layout.modalX() + layout.modalW();
            int modalBottom = layout.modalY() + layout.modalH();
            int closeSize = CreatePartyModalLayout.CLOSE_BUTTON_SIZE;

            assertTrue(layout.modalX() >= 0 && modalRight <= layout.screenWidth(), "modal is too wide at " + at);
            assertTrue(layout.modalY() >= 0 && modalBottom <= layout.screenHeight(), "modal is too tall at " + at);

            assertTrue(layout.closeButtonX() >= layout.modalX() && layout.closeButtonX() + closeSize <= modalRight
                    && layout.closeButtonY() >= layout.modalY(), "close button sticks out of the modal at " + at);
            assertTrue(layout.closeButtonY() + closeSize <= layout.bodyTop(), "close button overlaps the body at " + at);
            assertTrue(layout.titleY() + TEXT_HEIGHT <= layout.bodyTop(), "title overlaps the body at " + at);

            assertTrue(layout.bodyBottom(0) <= layout.saveY(), "body overlaps Save at " + at);
            assertTrue(layout.saveY() + CreatePartyModalLayout.SAVE_BUTTON_H <= modalBottom,
                    "Save sticks out of the modal at " + at);
        }
    }

    @Test
    void statusTextSitsBetweenTheBodyAndSave() {
        for (CreatePartyModalLayout layout : smallScreenLayouts()) {
            for (int lines = 1; lines <= MAX_STATUS_LINES; lines++) {
                String at = describe(layout) + " with " + lines + " status lines";
                int lastLineBottom = layout.statusTop(lines) + (lines - 1) * STATUS_LINE_SPACING + TEXT_HEIGHT;

                assertTrue(layout.bodyBottom(lines) <= layout.statusTop(lines), "body overlaps the status at " + at);
                assertTrue(lastLineBottom <= layout.saveY(), "status overlaps Save at " + at);
            }
        }
    }

    @Test
    void everyRowScrollsFullyIntoViewOnSmallScreens() {
        for (CreatePartyModalLayout layout : smallScreenLayouts()) {
            for (int lines = 0; lines <= MAX_STATUS_LINES; lines++) {
                for (Row row : layout.rows()) {
                    LayoutAssertions.assertScrollsIntoView(
                            row + " at " + describe(layout) + " with " + lines + " status lines",
                            cardTop(layout, row), cardBottom(layout, row),
                            layout.bodyTop(), layout.bodyBottom(lines), layout.bodyContentHeight());
                }
            }
        }
    }

    @Test
    void rowsDoNotOverlap() {
        for (boolean editing : new boolean[]{false, true}) {
            CreatePartyModalLayout layout = new CreatePartyModalLayout(TALL_W, TALL_H, editing);
            List<Row> rows = layout.rows();
            for (int i = 1; i < rows.size(); i++) {
                assertTrue(cardBottom(layout, rows.get(i - 1)) <= cardTop(layout, rows.get(i)),
                        rows.get(i - 1) + " overlaps " + rows.get(i) + " at " + describe(layout));
            }
            assertEquals(cardBottom(layout, rows.getLast()), layout.bodyContentBottom(),
                    "body content should end at the last card at " + describe(layout));
        }
    }

    @Test
    void nothingScrollsOnTallScreensEvenWithAThreeLineStatus() {
        for (boolean editing : new boolean[]{false, true}) {
            CreatePartyModalLayout layout = new CreatePartyModalLayout(TALL_W, TALL_H, editing);
            for (int lines = 0; lines <= MAX_STATUS_LINES; lines++) {
                assertTrue(layout.bodyContentHeight() <= layout.bodyBottom(lines) - layout.bodyTop(),
                        "body scrolls at " + describe(layout) + " with " + lines + " status lines");
            }
        }
    }

    // drawSectionCard draws each card 2px below its row top
    private static int cardTop(CreatePartyModalLayout layout, Row row) {
        return layout.rowTop(row) + 2;
    }

    private static int cardBottom(CreatePartyModalLayout layout, Row row) {
        return cardTop(layout, row) + row.height();
    }

    private static List<Integer> rowTops(CreatePartyModalLayout layout) {
        return layout.rows().stream().map(layout::rowTop).toList();
    }

    private static List<CreatePartyModalLayout> smallScreenLayouts() {
        List<CreatePartyModalLayout> layouts = new ArrayList<>();
        for (int[] size : SMALL_SCREENS) {
            layouts.add(new CreatePartyModalLayout(size[0], size[1], false));
            layouts.add(new CreatePartyModalLayout(size[0], size[1], true));
        }
        return layouts;
    }

    private static String describe(CreatePartyModalLayout layout) {
        return layout.screenWidth() + "x" + layout.screenHeight() + (layout.editing() ? " (editing)" : "");
    }
}

