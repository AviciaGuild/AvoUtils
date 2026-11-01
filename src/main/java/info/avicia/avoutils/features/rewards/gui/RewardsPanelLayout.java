package info.avicia.avoutils.features.rewards.gui;

/**
 * Where the rewards panel goes beside a container menu: left of it, up to {@link #MAX_WIDTH} wide, from the menu's
 * top to its bottom, and where the columns of its table go. Plain arithmetic with no Minecraft types, so tests check
 * it without a game.
 *
 * @param leftPos     the menu's left edge
 * @param topPos      the menu's top edge
 * @param imageHeight the menu's height
 */
public record RewardsPanelLayout(int screenWidth, int screenHeight, int leftPos, int topPos, int imageHeight) {
    static final int MAX_WIDTH = 170;
    /** Narrower than this, only a hint to lower the GUI scale is shown: the names would have no room. */
    static final int MIN_WIDTH = 140;
    /** Space to the screen edge and to the menu. */
    static final int MARGIN = 4;
    static final int BORDER = 1;
    /** Symmetric inner horizontal padding between the panel border and its header, table, status, and footer. */
    static final int PADDING = 5;
    /** The header, its top border and bottom separator included (1 + 15 + 1). */
    static final int HEADER_H = 17;
    /** The header's buttons, and the ✓ and ✕ of the slider's line. */
    static final int SMALL_BUTTON = 11;
    /** Square ▶ button size in the tome and aspect columns, and height of count pills. */
    static final int PLAY_SIZE = 9;
    static final int BUTTON_H = 13;
    static final int ROW_GAP = 3;
    static final int LINE_H = 9;
    static final int MIN_HEIGHT = 120;
    static final int TAB_W = 54;
    /** Symmetric vertical gap around status and footer sections. */
    static final int STATUS_GAP = 4;
    /** The table: top breathing gap, column titles, totals, separator, then a row per player. */
    static final int TABLE_TOP_GAP = 2;
    static final int TABLE_HEAD_H = 10;
    static final int TOTALS_H = 10;
    static final int ROW_H = 13;
    static final int EDITOR_H = 17;
    /** Reward column widths: LE holds just a count; Tome and Asp hold a count plus a square ▶ button. */
    static final int LE_COL_W = 20;
    static final int AUTO_COL_W = 28;
    static final int PLAY_GAP = 2;
    static final int COLUMN_GAP = 2;
    /** The progress bar under the status during a hand-out. */
    static final int BAR_H = 2;
    static final int BAR_GAP = 2;

    int width() {
        return Math.min(MAX_WIDTH, leftPos - 2 * MARGIN);
    }

    boolean showsHint() {
        return width() < MIN_WIDTH;
    }

    int x() {
        return right() - width();
    }

    int right() {
        return leftPos - MARGIN;
    }

    int y() {
        return Math.max(MARGIN, topPos);
    }

    int bottom() {
        return Math.min(screenHeight - MARGIN, Math.max(topPos + imageHeight, y() + MIN_HEIGHT));
    }

    /** Left edge of what's in the panel. */
    int contentLeft() {
        return x() + BORDER + PADDING;
    }

    int contentWidth() {
        return tableRight() - tableLeft();
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x() && mouseX < right() && mouseY >= y() && mouseY < bottom();
    }

    int listTop() {
        return y() + HEADER_H;
    }

    int headTop() {
        return listTop() + TABLE_TOP_GAP;
    }

    int totalsTop() {
        return headTop() + TABLE_HEAD_H;
    }

    int totalsSeparatorY() {
        return totalsTop() + TOTALS_H + 1;
    }

    /** Top of the players' rows: right below the totals separator line. */
    int rowsTop() {
        return totalsSeparatorY() + 1;
    }

    int tableLeft() {
        return contentLeft();
    }

    /** The table's right edge; any scrollbar sits in the right padding beyond it. */
    int tableRight() {
        return right() - BORDER - PADDING;
    }

    /** Right edge of reward column {@code column}: 0 for LE, 1 for tomes and 2 for aspects. */
    int columnRight(int column) {
        return tableRight() - (2 - column) * (AUTO_COL_W + COLUMN_GAP);
    }

    /** Left edge of the square ▶ in the tome and aspect columns. */
    int playX(int column) {
        return columnRight(column) - PLAY_SIZE;
    }

    /** Right edge of a column's count button: the column's edge for LE, left of ▶ for tomes and aspects. */
    int valueRight(int column) {
        return column == 0 ? columnRight(0) : playX(column) - PLAY_GAP;
    }

    /** Right edge of the names, left of the LE column. */
    int nameRight() {
        return columnRight(0) - LE_COL_W - COLUMN_GAP;
    }

    /** Top of the buttons, {@code rows} rows of them at the bottom, where they stay whatever the status says. */
    int buttonsTop(int rows) {
        return bottom() - BORDER - STATUS_GAP - rows * BUTTON_H - (rows - 1) * ROW_GAP;
    }

    /** Top of {@code lines} lines of status, with a progress bar under them if {@code bar}, just above the buttons. */
    int statusTop(int lines, boolean bar, int buttonRows) {
        if (lines == 0) return buttonsTop(buttonRows);
        return buttonsTop(buttonRows) - STATUS_GAP - lines * LINE_H - (bar ? BAR_GAP + BAR_H : 0);
    }

    /** Bottom of the list, where a 1px line separates it from the status/footer section below. */
    int listBottom(int statusLines, boolean bar, int buttonRows) {
        return statusTop(statusLines, bar, buttonRows) - STATUS_GAP - 1;
    }
}
