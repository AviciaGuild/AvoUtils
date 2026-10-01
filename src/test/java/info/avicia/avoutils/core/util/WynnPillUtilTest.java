package info.avicia.avoutils.core.util;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WynnPillUtilTest {

    @AfterEach
    void tearDown() {
        WynnPillUtil.setUsePillOverride(null);
        WynncraftServerPolicy.setScopeOverride(null);
    }

    @Nested
    class PillEnabledTests {

        @BeforeEach
        void setUp() {
            WynnPillUtil.setUsePillOverride(true);
        }

        @Test
        void createBuildsPillStructure() {
            Text pill = WynnPillUtil.create("a", Formatting.AQUA, Formatting.BLACK);
            String text = pill.getString();

            assertNotNull(text);
            assertTrue(text.startsWith("\uE010\u2064")); // left corner
            assertTrue(text.endsWith("\uE011\u2064")); // right corner
            assertTrue(text.contains("\uE00F")); // background back
            assertTrue(text.contains("\uE012\uE040")); // background front + glyph for 'a'
        }

        @Test
        void createLowercasesLettersAndMapsDigits() {
            String text = WynnPillUtil.create("A1", Formatting.AQUA, Formatting.BLACK).getString();

            assertTrue(text.contains("\uE040")); // 'a'
            assertTrue(text.contains("\uE061")); // '1'
        }

        @Test
        void createKeepsUnknownCharactersAsIs() {
            String text = WynnPillUtil.create("!", Formatting.AQUA, Formatting.BLACK).getString();
            assertTrue(text.contains("!"));
        }

        @Test
        void createPrefixedPillAppendsArrows() {
            String text = WynnPillUtil.createPrefixedPill("X", false).getString();

            assertTrue(text.startsWith("\uE010\u2064"));
            assertTrue(text.endsWith(" \u203A\u203A "));
        }

        @Test
        void createPrefixedPillSupportsErrorVariant() {
            String text = WynnPillUtil.createPrefixedPill("X", true).getString();
            assertTrue(text.endsWith(" \u203A\u203A "));
        }

        @Test
        void createClickableAttachesClickAndHoverEvents() {
            var pill = WynnPillUtil.createClickable("UPDATE", Formatting.GREEN, Formatting.BLACK,
                    "/avo update download", "Click to update");

            assertNotNull(pill.getStyle().getClickEvent());
            assertNotNull(pill.getStyle().getHoverEvent());
            assertTrue(pill.getString().startsWith("\uE010\u2064"));
            assertTrue(pill.getString().endsWith("\uE011\u2064"));
        }
    }

    @Nested
    class FallbackDisabledTests {

        @BeforeEach
        void setUp() {
            WynnPillUtil.setUsePillOverride(false);
        }

        @Test
        void createReturnsBracketedFallback() {
            Text fallback = WynnPillUtil.create("AvoUtils", Formatting.AQUA, Formatting.BLACK);
            assertEquals("[AvoUtils]", fallback.getString());
            assertFalse(fallback.getStyle().isBold());
            assertEquals(1, fallback.getSiblings().size());
            Text pillPart = fallback.getSiblings().get(0);
            assertTrue(pillPart.getStyle().isBold());
            assertEquals(TextColor.fromFormatting(Formatting.AQUA), pillPart.getStyle().getColor());
            assertFalse(fallback.getString().contains("\uE010"));
            assertFalse(fallback.getString().contains("\uE011"));
        }

        @Test
        void createPrefixedPillReturnsBracketedWithArrowsAndDoesNotBleedBold() {
            Text prefixed = WynnPillUtil.createPrefixedPill("AvoUtils", false);
            assertEquals("[AvoUtils] \u203A\u203A ", prefixed.getString());
            assertFalse(prefixed.getStyle().isBold());

            // Badge is bold and colored
            Text badge = prefixed.getSiblings().get(0);
            assertTrue(badge.getStyle().isBold());
            assertEquals(TextColor.fromFormatting(Formatting.AQUA), badge.getStyle().getColor());

            // Arrow separator is NOT bold
            Text arrow = prefixed.getSiblings().get(1);
            assertFalse(arrow.getStyle().isBold());

            // Subsequent message appended to the prefix is NOT bold
            Text fullMessage = prefixed.copy().append(Text.literal("Downloading update...").formatted(Formatting.GRAY));
            Text messageBody = fullMessage.getSiblings().get(2);
            assertFalse(messageBody.getStyle().isBold());
            assertFalse(prefixed.getString().contains("\uE010"));
        }

        @Test
        void createPrefixedPillSupportsErrorVariant() {
            Text prefixed = WynnPillUtil.createPrefixedPill("AvoUtils", true);
            assertEquals("[AvoUtils] \u203A\u203A ", prefixed.getString());
            assertFalse(prefixed.getStyle().isBold());

            Text badge = prefixed.getSiblings().get(0);
            assertTrue(badge.getStyle().isBold());
            assertEquals(TextColor.fromFormatting(Formatting.RED), badge.getStyle().getColor());
        }

        @Test
        void createClickableAttachesEventsToFallback() {
            var clickable = WynnPillUtil.createClickable("UPDATE", Formatting.GREEN, Formatting.BLACK,
                    "/avo update download", "Click to update");

            assertEquals("[UPDATE]", clickable.getString());
            assertFalse(clickable.getStyle().isBold());

            Text badge = clickable.getSiblings().get(0);
            assertTrue(badge.getStyle().isBold());
            assertEquals(TextColor.fromFormatting(Formatting.GREEN), badge.getStyle().getColor());
            assertNotNull(badge.getStyle().getClickEvent());
            assertInstanceOf(ClickEvent.RunCommand.class, badge.getStyle().getClickEvent());
            assertEquals("/avo update download", ((ClickEvent.RunCommand) badge.getStyle().getClickEvent()).command());

            assertNotNull(badge.getStyle().getHoverEvent());
            assertInstanceOf(HoverEvent.ShowText.class, badge.getStyle().getHoverEvent());
        }

        @Test
        void createFallbackDirectMethod() {
            Text fallback = WynnPillUtil.createFallback("RESTART", Formatting.GREEN, Formatting.BLACK);
            assertEquals("[RESTART]", fallback.getString());
            assertFalse(fallback.getStyle().isBold());
            Text badge = fallback.getSiblings().get(0);
            assertTrue(badge.getStyle().isBold());
            assertEquals(TextColor.fromFormatting(Formatting.GREEN), badge.getStyle().getColor());
        }
    }

    @Nested
    class PolicyResolutionTests {

        @Test
        void canUsePillRespectsOverride() {
            WynnPillUtil.setUsePillOverride(true);
            assertTrue(WynnPillUtil.canUsePill());

            WynnPillUtil.setUsePillOverride(false);
            assertFalse(WynnPillUtil.canUsePill());
        }

        @Test
        void canUsePillDelegatesToWynncraftServerPolicyWhenOverrideNull() {
            WynnPillUtil.setUsePillOverride(null);

            WynncraftServerPolicy.setScopeOverride(() -> WynncraftServerPolicy.Scope.MAIN);
            assertTrue(WynnPillUtil.canUsePill());

            WynncraftServerPolicy.setScopeOverride(() -> WynncraftServerPolicy.Scope.BETA);
            assertTrue(WynnPillUtil.canUsePill());

            WynncraftServerPolicy.setScopeOverride(() -> WynncraftServerPolicy.Scope.BLOCKED);
            assertFalse(WynnPillUtil.canUsePill());

            WynncraftServerPolicy.setScopeOverride(() -> WynncraftServerPolicy.Scope.UNKNOWN);
            assertFalse(WynnPillUtil.canUsePill());
        }
    }
}
