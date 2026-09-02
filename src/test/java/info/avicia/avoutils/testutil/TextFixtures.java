package info.avicia.avoutils.testutil;

import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

/**
 * Helpers for building Minecraft {@link Text} components in unit tests without a
 * running client. {@link Text} and friends are plain data classes provided by the
 * Loom minecraft dependency on the test classpath.
 */
public final class TextFixtures {

    private TextFixtures() {
    }

    /** A leaf text node whose hover event shows {@code hoverString}. */
    public static Text hoverText(String visible, String hoverString) {
        HoverEvent hoverEvent = new HoverEvent.ShowText(Text.literal(hoverString));
        return Text.literal(visible).setStyle(Style.EMPTY.withHoverEvent(hoverEvent));
    }

    /** A leaf text node with the given ARGB color on its style. */
    public static Text coloredText(String visible, int rgb) {
        return Text.literal(visible).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }
}
