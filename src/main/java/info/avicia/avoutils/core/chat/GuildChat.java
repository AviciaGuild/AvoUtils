package info.avicia.avoutils.core.chat;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Recognizes Wynncraft's guild chat (aqua) and territory war alerts (red) by leading color. */
public final class GuildChat {
    private static final int GUILD_CHAT_COLOR = 0x55FFFF;
    private static final int TERRITORY_ALERT_COLOR = 0xFF5555;

    private GuildChat() {
    }

    /** Whether the message starts in the guild chat colour (§b / 0x55FFFF). */
    public static boolean isGuildChat(Component message) {
        return hasLeadingColor(message, GUILD_CHAT_COLOR);
    }

    /** Whether the message starts in the territory/war alert colour (§c / 0xFF5555). */
    public static boolean isTerritoryAlert(Component message) {
        return hasLeadingColor(message, TERRITORY_ALERT_COLOR);
    }

    private static boolean hasLeadingColor(Component message, int expectedRgb) {
        if (message == null) return false;
        TextColor rootColor = message.getStyle().getColor();
        if (rootColor != null) return rootColor.getValue() == expectedRgb;
        Optional<Boolean> matches = message.visit((style, text) -> {
            if (text == null || text.isBlank()) return Optional.empty();
            TextColor color = style.getColor();
            return Optional.of(color != null && color.getValue() == expectedRgb);
        }, Style.EMPTY);
        return matches.orElse(false);
    }
}
