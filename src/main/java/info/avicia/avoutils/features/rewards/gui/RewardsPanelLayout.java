package info.avicia.avoutils.features.rewards.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the rewards panel goes beside a container menu: left of it, up to {@link #MAX_WIDTH} wide, from the menu's
 * top to its bottom. Plain arithmetic with no Minecraft types, so tests check it without a game.
 *
 * @param leftPos     the menu's left edge
 * @param topPos      the menu's top edge
 * @param imageHeight the menu's height
 */
public record RewardsPanelLayout(int screenWidth, int screenHeight, int leftPos, int topPos, int imageHeight) {
    static final int MAX_WIDTH = 170;
    /** Narrower than this, only a hint to lower the GUI scale is shown. */
    static final int MIN_WIDTH = 110;
    /** Space to the screen edge and to the menu. */
    static final int MARGIN = 4;
    static final int PADDING = 4;
    static final int HEADER_H = 16;
    static final int SUMMARY_H = 12;
    static final int BUTTON_H = 12;
    static final int ROW_GAP = 2;
    static final int LINE_H = 10;
    static final int MIN_HEIGHT = 120;
    static final int TAB_W = 62;

    /** A pill's place in the list: its x offset and its row. */
    record Position(int x, int row) {}

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

    int contentWidth() {
        return width() - 2 * PADDING;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x() && mouseX < right() && mouseY >= y() && mouseY < bottom();
    }

    int summaryY() {
        return y() + HEADER_H + 2;
    }

    int listTop() {
        return y() + HEADER_H + SUMMARY_H + 2;
    }

    /** Top of the footer: the emerald (or Stop) button, then {@code statusLines} lines of status. */
    int footerTop(int statusLines) {
        int statusHeight = statusLines > 0 ? ROW_GAP + statusLines * LINE_H : 0;
        return bottom() - PADDING - BUTTON_H - statusHeight;
    }

    int listBottom(int statusLines) {
        return footerTop(statusLines) - ROW_GAP;
    }

    int statusTop(int statusLines) {
        return footerTop(statusLines) + BUTTON_H + ROW_GAP;
    }

    /** Lays pills of the given widths out left to right, starting a new row when the next one doesn't fit. */
    static List<Position> flow(List<Integer> widths, int rowWidth, int gap) {
        List<Position> positions = new ArrayList<>();
        int x = 0;
        int row = 0;
        for (int width : widths) {
            if (x > 0 && x + width > rowWidth) {
                x = 0;
                row++;
            }
            positions.add(new Position(x, row));
            x += width + gap;
        }
        return positions;
    }
}
