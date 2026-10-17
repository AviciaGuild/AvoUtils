package info.avicia.avoutils.core.gui.config;

/**
 * Vertical layout of {@link ConfigScreen}: the card positions, the scrolling viewport and the Done
 * button for a given screen height. Plain arithmetic with no Minecraft types, so tests can check it
 * at small GUI sizes without starting the game.
 *
 * Card tops are unscrolled; subtract the viewport offset to draw them. Each card is drawn 2px
 * below its top, and its controls are placed relative to the top.
 *
 * @param updateCardHasAction whether the Updates card has a second row for a button or status line
 */
record ConfigScreenLayout(int screenHeight, boolean updateCardHasAction) {

    /** The cards, top to bottom. */
    enum Card { CHAT_BRIDGE, EMOJIS, PARTY_FINDER, GUILD_STORAGE, UPDATES }

    static final int DONE_BUTTON_H = 20;

    private static final int CARD_SINGLE_H = 48;
    private static final int CARD_DOUBLE_H = 66;
    private static final int CARD_QUAD_H = 102;
    private static final int CARD_GAP = 8;
    // Unscrolled Y of the first card; cards scroll under the title above VIEWPORT_TOP
    private static final int CONTENT_TOP = 38;
    private static final int VIEWPORT_TOP = 30;
    private static final int DONE_TOP_MARGIN = 16;
    private static final int DONE_BOTTOM_MARGIN = 8;

    /** Drawn height of a card. */
    int height(Card card) {
        return switch (card) {
            case CHAT_BRIDGE -> CARD_SINGLE_H;
            case EMOJIS, PARTY_FINDER -> CARD_DOUBLE_H;
            case GUILD_STORAGE -> CARD_QUAD_H;
            case UPDATES -> updateCardHasAction ? CARD_DOUBLE_H : CARD_SINGLE_H;
        };
    }

    /** Unscrolled Y of a card's top. */
    int top(Card card) {
        int y = CONTENT_TOP;
        for (Card above : Card.values()) {
            if (above == card) {
                return y;
            }
            y += height(above) + CARD_GAP;
        }
        throw new IllegalArgumentException(String.valueOf(card));
    }

    /** Unscrolled Y where the content ends: below the last card and one card gap. */
    int contentBottom() {
        int y = CONTENT_TOP;
        for (Card card : Card.values()) {
            y += height(card) + CARD_GAP;
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

