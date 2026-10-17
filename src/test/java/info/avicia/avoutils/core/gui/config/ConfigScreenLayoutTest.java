package info.avicia.avoutils.core.gui.config;

import info.avicia.avoutils.core.gui.CompatibilityHelper;
import info.avicia.avoutils.core.gui.LayoutAssertions;
import info.avicia.avoutils.core.gui.config.ConfigScreenLayout.Card;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigScreenLayoutTest {

    // Auto GUI scale on 1440p (also 720p and 4K), Auto on 1080p, and GUI scale 3 on 1080p
    private static final int[] SMALL_HEIGHTS = {240, 270, 360};
    // GUI scale 2 on 1080p
    private static final int TALL_HEIGHT = 540;
    private static final int TEXT_HEIGHT = 9;
    private static final boolean[] UPDATE_ACTION = {false, true};

    @Test
    void tallScreenKeepsTheOriginalPositions() {
        ConfigScreenLayout layout = new ConfigScreenLayout(TALL_HEIGHT, false);

        assertArrayEquals(new int[]{38, 94, 168, 242, 352}, tops(layout));
        assertArrayEquals(new int[]{48, 66, 66, 102, 48}, heights(layout));
        assertEquals(30, layout.viewportTop());
        assertEquals(416, layout.viewportBottom());
        assertEquals(378, layout.contentHeight());
        assertEquals(424, layout.doneY());
    }

    @Test
    void updateActionMakesTheUpdatesCardTaller() {
        ConfigScreenLayout layout = new ConfigScreenLayout(TALL_HEIGHT, true);

        assertEquals(352, layout.top(Card.UPDATES));
        assertEquals(66, layout.height(Card.UPDATES));
        assertEquals(396, layout.contentHeight());
        assertEquals(442, layout.doneY());
    }

    @Test
    void cardsDoNotOverlap() {
        for (boolean hasAction : UPDATE_ACTION) {
            ConfigScreenLayout layout = new ConfigScreenLayout(TALL_HEIGHT, hasAction);
            Card[] cards = Card.values();
            for (int i = 1; i < cards.length; i++) {
                assertTrue(cardBottom(layout, cards[i - 1]) <= cardTop(layout, cards[i]),
                        cards[i - 1] + " overlaps " + cards[i]);
            }
        }
    }

    @Test
    void doneButtonAndTitleStayClearOfTheCardsOnSmallScreens() {
        for (int height : SMALL_HEIGHTS) {
            for (boolean hasAction : UPDATE_ACTION) {
                ConfigScreenLayout layout = new ConfigScreenLayout(height, hasAction);
                String at = describe(layout);

                assertTrue(layout.doneY() + ConfigScreenLayout.DONE_BUTTON_H <= height, "Done is off-screen at " + at);
                assertTrue(layout.viewportBottom() <= layout.doneY(), "cards scroll over Done at " + at);
                assertTrue(layout.viewportTop() >= CompatibilityHelper.SCREEN_TITLE_Y + TEXT_HEIGHT,
                        "cards scroll over the title at " + at);
            }
        }
    }

    @Test
    void everyCardScrollsFullyIntoViewOnSmallScreens() {
        for (int height : SMALL_HEIGHTS) {
            for (boolean hasAction : UPDATE_ACTION) {
                ConfigScreenLayout layout = new ConfigScreenLayout(height, hasAction);
                for (Card card : Card.values()) {
                    LayoutAssertions.assertScrollsIntoView(card + " at " + describe(layout),
                            cardTop(layout, card), cardBottom(layout, card),
                            layout.viewportTop(), layout.viewportBottom(), layout.contentHeight());
                }
            }
        }
    }

    @Test
    void nothingScrollsOnTallScreens() {
        for (boolean hasAction : UPDATE_ACTION) {
            ConfigScreenLayout layout = new ConfigScreenLayout(TALL_HEIGHT, hasAction);

            assertTrue(layout.contentHeight() <= layout.viewportBottom() - layout.viewportTop(),
                    "content scrolls at " + describe(layout));
        }
    }

    // drawSectionCard draws each card 2px below its top
    private static int cardTop(ConfigScreenLayout layout, Card card) {
        return layout.top(card) + 2;
    }

    private static int cardBottom(ConfigScreenLayout layout, Card card) {
        return cardTop(layout, card) + layout.height(card);
    }

    private static int[] tops(ConfigScreenLayout layout) {
        return Arrays.stream(Card.values()).mapToInt(layout::top).toArray();
    }

    private static int[] heights(ConfigScreenLayout layout) {
        return Arrays.stream(Card.values()).mapToInt(layout::height).toArray();
    }

    private static String describe(ConfigScreenLayout layout) {
        return "height " + layout.screenHeight() + (layout.updateCardHasAction() ? " with an update action" : "");
    }
}

