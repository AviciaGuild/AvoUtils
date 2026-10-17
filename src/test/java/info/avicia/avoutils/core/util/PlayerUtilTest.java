package info.avicia.avoutils.core.util;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

class PlayerUtilTest {

    @Test
    void namesEqualIsNullSafe() {
        assertFalse(PlayerUtil.namesEqual(null, null));
        assertFalse(PlayerUtil.namesEqual(null, "Steve"));
        assertFalse(PlayerUtil.namesEqual("Steve", null));
    }

    @Test
    void namesEqualIsCaseInsensitive() {
        assertTrue(PlayerUtil.namesEqual("Steve", "steve"));
        assertTrue(PlayerUtil.namesEqual("STEVE", "Steve"));
        assertFalse(PlayerUtil.namesEqual("Steve", "Alex"));
    }

    @Test
    void normalizeNameLowercasesWithRootLocale() {
        assertEquals("", PlayerUtil.normalizeName(null));
        assertEquals("steve", PlayerUtil.normalizeName("Steve"));
        assertEquals("steve123", PlayerUtil.normalizeName("STEVE123"));
    }

    @Test
    void containsIgnoreCaseHandlesNullsAndEdgeCases() {
        assertFalse(PlayerUtil.containsIgnoreCase(null, "test"));
        assertFalse(PlayerUtil.containsIgnoreCase("test", null));
        assertFalse(PlayerUtil.containsIgnoreCase(null, null));
        assertTrue(PlayerUtil.containsIgnoreCase("hello world", ""));
        assertTrue(PlayerUtil.containsIgnoreCase("hello world", "WORLD"));
        assertTrue(PlayerUtil.containsIgnoreCase("Rewards are unavailable for this rank", "rewards are unavailable"));
        assertFalse(PlayerUtil.containsIgnoreCase("hello", "world"));
    }

    @Test
    void selfNameReturnsSessionUsername() {
        try (MockedStatic<Minecraft> mc = mockStatic(Minecraft.class)) {
            Minecraft client = mock(Minecraft.class);
            mc.when(Minecraft::getInstance).thenReturn(client);
            User session = mock(User.class);
            when(client.getUser()).thenReturn(session);
            when(session.getName()).thenReturn("Steve");

            assertEquals("Steve", PlayerUtil.selfName());
        }
    }

    @Test
    void selfNameReturnsNullWithoutSession() {
        try (MockedStatic<Minecraft> mc = mockStatic(Minecraft.class)) {
            Minecraft client = mock(Minecraft.class);
            mc.when(Minecraft::getInstance).thenReturn(client);
            when(client.getUser()).thenReturn(null);

            assertNull(PlayerUtil.selfName());
        }
    }

    @Test
    void selfNameReturnsNullWhenClientIsNull() {
        try (MockedStatic<Minecraft> mc = mockStatic(Minecraft.class)) {
            mc.when(Minecraft::getInstance).thenReturn(null);
            assertNull(PlayerUtil.selfName());
        }
    }

    @Test
    void isSelfReturnsFalseForNull() {
        assertFalse(PlayerUtil.isSelf(null));
    }

    @Test
    void isSelfMatchesSessionNameWhenPlayerEntityNull() {
        try (MockedStatic<Minecraft> mc = mockStatic(Minecraft.class)) {
            Minecraft client = mock(Minecraft.class);
            mc.when(Minecraft::getInstance).thenReturn(client);
            User session = mock(User.class);
            when(client.getUser()).thenReturn(session);
            when(session.getName()).thenReturn("Steve");

            assertTrue(PlayerUtil.isSelf("Steve"));
            assertTrue(PlayerUtil.isSelf("steve"));
            assertFalse(PlayerUtil.isSelf("Alex"));
        }
    }
}
