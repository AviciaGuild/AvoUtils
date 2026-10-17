package info.avicia.avoutils.testutil;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Helpers for building Minecraft {@link Component} components in unit tests without a
 * running client. {@link Component} and friends are plain data classes provided by the
 * Loom minecraft dependency on the test classpath.
 */
public final class TextFixtures {

    private TextFixtures() {
    }

    /** A leaf text node whose hover event shows {@code hoverString}. */
    public static Component hoverText(String visible, String hoverString) {
        HoverEvent hoverEvent = new HoverEvent.ShowText(Component.literal(hoverString));
        return Component.literal(visible).setStyle(Style.EMPTY.withHoverEvent(hoverEvent));
    }

    /** A leaf text node with the given ARGB color on its style. */
    public static Component coloredText(String visible, int rgb) {
        return Component.literal(visible).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }
}
