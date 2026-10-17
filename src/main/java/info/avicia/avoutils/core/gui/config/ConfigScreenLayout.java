package info.avicia.avoutils.core.gui.config;

import info.avicia.avoutils.core.config.ConfigSection;
import info.avicia.avoutils.core.config.Settings;

/**
 * Vertical layout of {@link ConfigScreen}: one card per {@link ConfigSection}, with a row for each
 * of its settings, then the scrolling viewport and the Done button. Plain arithmetic with no
 * Minecraft types, so tests can check it at small GUI sizes without starting the game.
 *
 * Card tops are unscrolled; subtract the viewport offset to draw them. Each card is drawn 2px
 * below its top, and its rows are placed relative to the top.
 *
 * @param updateCardHasAction whether the Updates card has an extra row for a button or status line
 */
record ConfigScreenLayout(int screenHeight, boolean updateCardHasAction) {

    static final int DONE_BUTTON_H = 20;

    // Card title area; each row adds ROW_H
    private static final int CARD_HEADER_H = 30;
    private static final int ROW_H = 18;
    private static final int FIRST_ROW_OFFSET = 28;
    private static final int CARD_GAP = 8;
    // Unscrolled Y of the first card; cards scroll under the title above VIEWPORT_TOP
    private static final int CONTENT_TOP = 38;
    private static final int VIEWPORT_TOP = 30;
    private static final int DONE_TOP_MARGIN = 16;
    private static final int DONE_BOTTOM_MARGIN = 8;

    /** Rows in a section's card: one per setting, plus the Updates card's action row if it has one. */
    int rows(ConfigSection section) {
        int rows = Settings.inSection(section).size();
        return section == ConfigSection.UPDATES && updateCardHasAction ? rows + 1 : rows;
    }

    /** Drawn height of a section's card. */
    int height(ConfigSection section) {
        return CARD_HEADER_H + rows(section) * ROW_H;
    }

    /** Unscrolled Y of a section card's top. */
    int top(ConfigSection section) {
        int y = CONTENT_TOP;
        for (ConfigSection above : ConfigSection.values()) {
            if (above == section) {
                return y;
            }
            y += height(above) + CARD_GAP;
        }
        throw new IllegalArgumentException(String.valueOf(section));
    }

    /**
     * Offset from a card's top to a row's line. Toggles (16px) sit on the line; sliders and labels
     * are centred on the same row.
     */
    static int rowOffset(int row) {
        return FIRST_ROW_OFFSET + row * ROW_H;
    }

    /** Unscrolled Y of a row's line. */
    int rowY(ConfigSection section, int row) {
        return top(section) + rowOffset(row);
    }

    /** Unscrolled Y of the Updates card's action row, after its settings, for the button and status text. */
    int updateActionRowY() {
        return rowY(ConfigSection.UPDATES, Settings.inSection(ConfigSection.UPDATES).size());
    }

    /** Unscrolled Y where the content ends: below the last card and one card gap. */
    int contentBottom() {
        int y = CONTENT_TOP;
        for (ConfigSection section : ConfigSection.values()) {
            y += height(section) + CARD_GAP;
        }
        return y;
    }

    /** The Done button follows the content, but is pinned to the bottom of short screens. */
    int doneY() {
        return Math.min(contentBottom() + DONE_TOP_MARGIN, screenHeight - DONE_BUTTON_H - DONE_BOTTOM_MARGIN);
    }

    int viewportTop() {
        return VIEWPORT_TOP;
    }

    /** The viewport ends one card gap above the Done button. */
    int viewportBottom() {
        return doneY() - CARD_GAP;
    }

    /** Content height measured from the viewport top, as {@code ScrollViewport.update} expects. */
    int contentHeight() {
        return contentBottom() - VIEWPORT_TOP;
    }
}
