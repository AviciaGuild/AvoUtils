package info.avicia.avoutils.core.chat;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Recognizes Wynncraft's guild chat, including guild broadcasts such as reward grants. */
public final class GuildChat {
    private static final int GUILD_CHAT_COLOR = 0x55FFFF;

    private GuildChat() {
    }

    /** Whether the message starts in the guild chat colour. */
    public static boolean isGuildChat(Component message) {
        if (message == null) return false;
        TextColor rootColor = message.getStyle().getColor();
        if (rootColor != null) return rootColor.getValue() == GUILD_CHAT_COLOR;
        Optional<Boolean> leadingColorIsGuild = message.visit((style, text) -> {
            if (text == null || text.isBlank()) return Optional.empty();
            TextColor color = style.getColor();
            return Optional.of(color != null && color.getValue() == GUILD_CHAT_COLOR);
        }, Style.EMPTY);
        return leadingColorIsGuild.orElse(false);
    }
}
