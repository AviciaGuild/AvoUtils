package info.avicia.avoutils.core.chat;

import info.avicia.avoutils.testutil.TextFixtures;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildChatTest {

    private static final int GUILD_CHAT_COLOR = 0x55FFFF;

    @Test
    void leadingGuildColorIsDetectedOnRootStyle() {
        assertTrue(GuildChat.isGuildChat(TextFixtures.coloredText("hi", GUILD_CHAT_COLOR)));
    }

    @Test
    void leadingGuildColorIsDetectedOnLeafStyle() {
        Component message = Component.empty().append(TextFixtures.coloredText("hi", GUILD_CHAT_COLOR));
        assertTrue(GuildChat.isGuildChat(message));
    }

    @Test
    void otherColorsAreNotGuildChat() {
        assertFalse(GuildChat.isGuildChat(TextFixtures.coloredText("hi", 0xFF0000)));
        assertFalse(GuildChat.isGuildChat(Component.literal("hi")));
    }
}
