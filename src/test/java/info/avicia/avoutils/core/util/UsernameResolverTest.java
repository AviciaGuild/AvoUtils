package info.avicia.avoutils.core.util;

import info.avicia.avoutils.testutil.TextFixtures;
import net.minecraft.network.chat.Component;
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
        assertEquals("Steve", UsernameResolver.resolve(Component.literal("Steve: hi"), "Steve"));
    }

    @Test
    void resolveExtractsHoverRealName() {
        Component message = TextFixtures.hoverText("Steve", "Real Username: NotSteve");
        assertEquals("NotSteve", UsernameResolver.resolve(message, "Steve"));
    }

    @Test
    void resolveExtractsApostropheHoverForm() {
        Component message = TextFixtures.hoverText("Steve", "'s real name is NotSteve");
        assertEquals("NotSteve", UsernameResolver.resolve(message, "Steve"));
    }

    @Test
    void resolveReturnsNullForInvalidNameWithoutHover() {
        assertNull(UsernameResolver.resolve(Component.literal("ab"), "ab"));
    }

    @Test
    void resolveExtractsRealNameFromClassNickname() {
        Component message = TextFixtures.hoverText("avo ignis war dps", "'s real name is CupBoi");
        assertEquals("CupBoi", UsernameResolver.resolve(message, "avo ignis war dps"));
    }

    @Test
    void resolveDoesNotBleedHoverTextToOtherNames() {
        // Simulates a Wynncraft message where the sender has hover text but the recipient does not.
        net.minecraft.network.chat.MutableComponent message = net.minecraft.network.chat.Component.empty()
                .append(info.avicia.avoutils.testutil.TextFixtures.hoverText("CupBoi", "'s real name is RealCupBoi"))
                .append(" rewarded 1,000 Emeralds to ")
                .append("Krokofant");
                
        // Sender has hover text, should resolve to RealCupBoi
        org.junit.jupiter.api.Assertions.assertEquals("RealCupBoi", UsernameResolver.resolve(message, "CupBoi"));
        
        // Recipient does NOT have hover text, should fall back to just Krokofant without bleeding
        org.junit.jupiter.api.Assertions.assertEquals("Krokofant", UsernameResolver.resolve(message, "Krokofant"));
    }

    @Test
    void resolveMatchesNamesAsWholeWords() {
        Component bobToBobby = Component.empty()
                .append(TextFixtures.hoverText("Bob", "'s real name is RealBob"))
                .append(" rewarded a Guild Tome to ")
                .append(TextFixtures.hoverText("Bobby", "'s real name is RealBobby"));
        assertEquals("RealBob", UsernameResolver.resolve(bobToBobby, "Bob"));
        assertEquals("RealBobby", UsernameResolver.resolve(bobToBobby, "Bobby"));

        Component bobbyToBob = Component.empty()
                .append(TextFixtures.hoverText("Bobby", "'s real name is RealBobby"))
                .append(" rewarded a Guild Tome to ")
                .append(TextFixtures.hoverText("Bob", "'s real name is RealBob"));
        assertEquals("RealBob", UsernameResolver.resolve(bobbyToBob, "Bob"));
        assertEquals("RealBobby", UsernameResolver.resolve(bobbyToBob, "Bobby"));
    }

    @Test
    void resolveDoesNotGiveANicknamedPlayersNameToALongerName() {
        Component message = Component.empty()
                .append(TextFixtures.hoverText("Bob", "'s real name is RealBob"))
                .append(" rewarded a Guild Tome to Bobby");
        assertEquals("Bobby", UsernameResolver.resolve(message, "Bobby"));
    }

    @Test
    void resolveMatchesANameInsideALongerSpan() {
        Component message = TextFixtures.hoverText("[Chief] Bob", "'s real name is RealBob");
        assertEquals("RealBob", UsernameResolver.resolve(message, "Bob"));
    }

    @Test
    void resolveReadsTheColouredRealUsernameHover() {
        Component message = TextFixtures.hoverText("§fI Burger", "§fI Burger§7's real username is §fpat_crafter07");
        assertEquals("pat_crafter07", UsernameResolver.resolve(message, "I Burger"));
    }
}