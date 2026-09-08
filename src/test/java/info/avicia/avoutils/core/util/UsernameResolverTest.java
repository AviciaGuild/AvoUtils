package info.avicia.avoutils.core.util;

import info.avicia.avoutils.testutil.TextFixtures;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsernameResolverTest {

    @Test
    void isValidEnforcesUsernameRules() {
        assertFalse(UsernameResolver.isValid(null));
        assertFalse(UsernameResolver.isValid("ab"));
        assertFalse(UsernameResolver.isValid("abcdefghijklmnopq"));
        assertTrue(UsernameResolver.isValid("Steve123"));
        assertTrue(UsernameResolver.isValid("Steve_Bob"));
    }

    @Test
    void resolveFallsBackToValidDisplayName() {
        assertEquals("Steve", UsernameResolver.resolve(Text.literal("Steve: hi"), "Steve"));
    }

    @Test
    void resolveExtractsHoverRealName() {
        Text message = TextFixtures.hoverText("Steve", "Real Username: NotSteve");
        assertEquals("NotSteve", UsernameResolver.resolve(message, "Steve"));
    }

    @Test
    void resolveExtractsApostropheHoverForm() {
        Text message = TextFixtures.hoverText("Steve", "'s real name is NotSteve");
        assertEquals("NotSteve", UsernameResolver.resolve(message, "Steve"));
    }

    @Test
    void resolveReturnsNullForInvalidNameWithoutHover() {
        assertNull(UsernameResolver.resolve(Text.literal("ab"), "ab"));
    }

    @Test
    void resolveExtractsRealNameFromClassNickname() {
        Text message = TextFixtures.hoverText("avo ignis war dps", "'s real name is CupBoi");
        assertEquals("CupBoi", UsernameResolver.resolve(message, "avo ignis war dps"));
    }
}
