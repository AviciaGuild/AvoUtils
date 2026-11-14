package info.avicia.avoutils.features.chatbridge;

import info.avicia.avoutils.testutil.TextFixtures;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BankDetectorTest {

    @Test
    void detectsDepositWithTier() {
        BankDetector.Result result =
                BankDetector.tryDetect("Steve deposited 5 Emeralds to the Guild Bank (General)", Component.literal(""));
        assertNotNull(result);
        assertEquals("Steve", result.player());
        assertEquals("deposited", result.action());
        assertEquals("5 Emeralds", result.item());
        assertEquals("General", result.tier());
    }

    @Test
    void detectsWithdrawalWithoutTier() {
        BankDetector.Result result =
                BankDetector.tryDetect("Steve withdrew 100 Emeralds from the Guild Bank", Component.literal(""));
        assertNotNull(result);
        assertEquals("Steve", result.player());
        assertEquals("withdrew", result.action());
        assertEquals("100 Emeralds", result.item());
        assertNull(result.tier());
    }

    @Test
    void keepsUnderscoresUnescapedInPlayerUsername() {
        BankDetector.Result result =
                BankDetector.tryDetect("Steve_Bob deposited 5 Emeralds to the Guild Bank (General)", Component.literal(""));
        assertNotNull(result);
        assertEquals("Steve_Bob", result.player());
    }

    @Test
    void returnsNullForNonBankMessages() {
        assertNull(BankDetector.tryDetect("Steve says hi", Component.literal("")));
        assertNull(BankDetector.tryDetect("The Guild is great", Component.literal("")));
    }

    @Test
    void returnsNullWhenUsernameCannotBeResolved() {
        assertNull(BankDetector.tryDetect("ab deposited 5 Emeralds to the Guild Bank", Component.literal("")));
    }

    @Test
    void returnsNullForNullCleaned() {
        assertNull(BankDetector.tryDetect(null, Component.literal("")));
    }

    @Test
    void ignoresPlayerChatMentioningBankDeposit() {
        Component chat = Component.empty()
                .append(TextFixtures.hoverText("Steve", "'s real name is RealSteve"))
                .append(": I deposited 5 Emeralds to the Guild Bank (General)");
        assertNull(BankDetector.tryDetect("Steve: I deposited 5 Emeralds to the Guild Bank (General)", chat));
    }
}
