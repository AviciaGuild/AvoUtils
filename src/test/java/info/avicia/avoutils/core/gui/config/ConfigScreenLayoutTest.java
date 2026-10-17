package info.avicia.avoutils.core.gui.config;

import info.avicia.avoutils.core.config.ConfigSection;
import info.avicia.avoutils.core.gui.CompatibilityHelper;
import info.avicia.avoutils.core.gui.LayoutAssertions;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
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
    // The update/restart button: 18px tall, starting 2px above its row's line
    private static final int ACTION_BUTTON_H = 18;
    private static final int ACTION_BUTTON_OFFSET = -2;

    // The card heights come from the settings declared in each section, so these also check that
    // the generated screen matches the hand-placed one it replaced
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

        assertEquals(352, layout.top(ConfigSection.UPDATES));
        assertEquals(66, layout.height(ConfigSection.UPDATES));
        assertEquals(396, layout.contentHeight());
        assertEquals(442, layout.doneY());
    }

    @Test
    void rowsKeepTheirOriginalOffsets() {
        assertEquals(List.of(28, 46, 64, 82), IntStream.range(0, 4).map(ConfigScreenLayout::rowOffset).boxed().toList());

        ConfigScreenLayout layout = new ConfigScreenLayout(TALL_HEIGHT, true);
        assertEquals(layout.top(ConfigSection.UPDATES) + 44, layout.updateActionRowY() + ACTION_BUTTON_OFFSET);
    }

    @Test
    void rowsFitInsideTheirCard() {
        for (boolean hasAction : UPDATE_ACTION) {
            ConfigScreenLayout layout = new ConfigScreenLayout(TALL_HEIGHT, hasAction);
            for (ConfigSection section : ConfigSection.values()) {
                int lastRowY = layout.rowY(section, layout.rows(section) - 1);
                // Toggles are the tallest setting control: 16px from the row's line
                assertTrue(lastRowY + 16 <= cardBottom(layout, section), section + " rows overflow its card");
            }

            if (hasAction) {
                int buttonTop = layout.updateActionRowY() + ACTION_BUTTON_OFFSET;
                assertTrue(buttonTop >= cardTop(layout, ConfigSection.UPDATES)
                        && buttonTop + ACTION_BUTTON_H <= cardBottom(layout, ConfigSection.UPDATES),
                        "the update button overflows the Updates card");
            }
        }
    }

    @Test
    void cardsDoNotOverlap() {
        for (boolean hasAction : UPDATE_ACTION) {
            ConfigScreenLayout layout = new ConfigScreenLayout(TALL_HEIGHT, hasAction);
            ConfigSection[] sections = ConfigSection.values();
            for (int i = 1; i < sections.length; i++) {
                assertTrue(cardBottom(layout, sections[i - 1]) <= cardTop(layout, sections[i]),
                        sections[i - 1] + " overlaps " + sections[i]);
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
                for (ConfigSection section : ConfigSection.values()) {
                    LayoutAssertions.assertScrollsIntoView(section + " at " + describe(layout),
                            cardTop(layout, section), cardBottom(layout, section),
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
    private static int cardTop(ConfigScreenLayout layout, ConfigSection section) {
        return layout.top(section) + 2;
    }

    private static int cardBottom(ConfigScreenLayout layout, ConfigSection section) {
        return cardTop(layout, section) + layout.height(section);
    }

    private static int[] tops(ConfigScreenLayout layout) {
        return Arrays.stream(ConfigSection.values()).mapToInt(layout::top).toArray();
    }

    private static int[] heights(ConfigScreenLayout layout) {
        return Arrays.stream(ConfigSection.values()).mapToInt(layout::height).toArray();
    }

    private static String describe(ConfigScreenLayout layout) {
        return "height " + layout.screenHeight() + (layout.updateCardHasAction() ? " with an update action" : "");
    }
}
