package info.avicia.avoutils.features.chatbridge;

import info.avicia.avoutils.testutil.TextFixtures;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GuildChatDetectorTest {

    @Test
    void detectsDirectAndNicknamedGuildChat() {
        GuildChatDetector.Result direct =
                GuildChatDetector.tryDetect("Steve: hello guild :wave:", Component.literal("Steve: hello guild :wave:"));
        assertNotNull(direct);
        assertEquals("Steve", direct.username());
        assertEquals("hello guild :wave:", direct.content());
        assertEquals("Steve\u0000hello guild", direct.dedupKey());

        Component nickComp = Component.empty()
                .append(TextFixtures.hoverText("avo ignis war dps", "'s real name is CupBoi"))
                .append(": lfg tna");
        GuildChatDetector.Result nicked =
                GuildChatDetector.tryDetect("<1> avo ignis war dps: lfg tna", nickComp);
        assertNotNull(nicked);
        assertEquals("CupBoi", nicked.username());
        assertEquals("lfg tna", nicked.content());
    }

    @Test
    void returnsNullForEmptyContentOrUnresolvedSpeaker() {
        assertNull(GuildChatDetector.tryDetect("Steve:   ", Component.literal("")));
        assertNull(GuildChatDetector.tryDetect("unresolvable nick: hello", Component.literal("")));
        assertNull(GuildChatDetector.tryDetect("system announcement without colon", Component.literal("")));
        assertNull(GuildChatDetector.tryDetect(null, Component.literal("")));
    }
}
