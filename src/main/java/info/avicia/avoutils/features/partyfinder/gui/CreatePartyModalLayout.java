package info.avicia.avoutils.features.partyfinder.gui;

import java.util.List;

/**
 * Layout of {@link CreatePartyModal}: the modal frame, header, form rows, status text and Save
 * button for a given screen size. Plain arithmetic with no Minecraft types, so tests can check it
 * at small GUI sizes without starting the game.
 *
 * Row tops are unscrolled; subtract the body viewport offset to draw them. Each card is drawn
 * 2px below its row top, and its controls are placed relative to the row top.
 *
 * @param editing whether an existing party is being edited, which has no Reserved Slots row
 */
record CreatePartyModalLayout(int screenWidth, int screenHeight, boolean editing) {

    /** Form rows, top to bottom, with the drawn height of each card. */
    enum Row {
        ACTIVITY(30), ROLE(30), REGION(30), NOTE(42), RESERVED(30), PING(30);

        private final int height;

        Row(int height) {
            this.height = height;
        }

        int height() {
            return height;
        }
    }

    static final int CLOSE_BUTTON_SIZE = 12;
    static final int SAVE_BUTTON_H = 18;

    private static final int MAX_WIDTH = 320;
    private static final int CREATE_HEIGHT = 334;
    private static final int EDIT_HEIGHT = 296;
    // Smallest total space left around the modal on each axis
    private static final int SCREEN_MARGIN = 40;
    // Header stripe and separator drawn by CompatibilityHelper.drawModalFrame
    private static final int HEADER_H = 25;
    private static final int FIRST_ROW_OFFSET = 38;
    private static final int ROW_GAP = 8;
    // Space under the body (or status text) for the Save button
    private static final int FOOTER_H = 32;
    private static final int SAVE_BOTTOM_MARGIN = 8;
    private static final int STATUS_LINE_H = 10;

    private static final List<Row> CREATE_ROWS = List.of(Row.values());
    private static final List<Row> EDIT_ROWS = List.of(Row.ACTIVITY, Row.ROLE, Row.REGION, Row.NOTE, Row.PING);

    int modalW() {
        return Math.min(MAX_WIDTH, screenWidth - SCREEN_MARGIN);
    }

    int modalH() {
        return Math.min(editing ? EDIT_HEIGHT : CREATE_HEIGHT, screenHeight - SCREEN_MARGIN);
    }

    int modalX() {
        return (screenWidth - modalW()) / 2;
    }

    int modalY() {
        return (screenHeight - modalH()) / 2;
    }

    int closeButtonX() {
        return modalX() + modalW() - 16;
    }

    int closeButtonY() {
        return modalY() + 6;
    }

    int titleY() {
        return modalY() + 8;
    }

    /** The rows shown, top to bottom. */
    List<Row> rows() {
        return editing ? EDIT_ROWS : CREATE_ROWS;
    }

    /** Unscrolled Y of a row's top. */
    int rowTop(Row row) {
        int y = modalY() + FIRST_ROW_OFFSET;
        for (Row above : rows()) {
            if (above == row) {
                return y;
            }
            y += above.height() + ROW_GAP;
        }
        throw new IllegalArgumentException(row + " isn't shown" + (editing ? " when editing" : ""));
    }

    /** Top of the scrolling body, just below the header. */
    int bodyTop() {
        return modalY() + HEADER_H;
    }

    /** Unscrolled Y of the last card's bottom edge. */
    int bodyContentBottom() {
        Row last = rows().getLast();
        return rowTop(last) + 2 + last.height();
    }

    /** Content height measured from the body top, as {@code ScrollViewport.update} expects. */
    int bodyContentHeight() {
        return bodyContentBottom() - bodyTop();
    }

    /** Bottom of the scrolling body: just above the status text, or the footer when there's none. */
    int bodyBottom(int statusLines) {
        return statusLines > 0 ? statusTop(statusLines) - 2 : statusBottom();
    }

    /** Bottom of the status text, which sits just above the Save button. */
    int statusBottom() {
        return modalY() + modalH() - FOOTER_H;
    }

    /** Top of a status message wrapped to {@code lines} lines. */
    int statusTop(int lines) {
        return statusBottom() - (lines * STATUS_LINE_H - 2);
    }

    int saveY() {
        return modalY() + modalH() - SAVE_BUTTON_H - SAVE_BOTTOM_MARGIN;
    }
}

