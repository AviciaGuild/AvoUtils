package info.avicia.avoutils.features.chatbridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.network.chat.Component;

class BankDetectorTest {

    @Test
    void detectsDepositWithTier() {
        BankDetector.Result result =
                BankDetector.tryDetect("Steve deposited 5 Emeralds to the Guild Bank (General)", Component.literal(""));
        assertNotNull(result);
        assertEquals("Guild Bank (General)", result.displayName());
        assertEquals("**Steve** deposited **5 Emeralds**", result.formattedMessage());
    }

    @Test
    void detectsWithdrawalWithoutTier() {
        BankDetector.Result result =
                BankDetector.tryDetect("Steve withdrew 100 Emeralds from the Guild Bank", Component.literal(""));
        assertNotNull(result);
        assertEquals("Guild Bank", result.displayName());
        assertEquals("**Steve** withdrew **100 Emeralds**", result.formattedMessage());
    }

    @Test
    void escapesUnderscoresInUsername() {
        BankDetector.Result result =
                BankDetector.tryDetect("Steve_Bob deposited 5 Emeralds to the Guild Bank (General)", Component.literal(""));
        assertNotNull(result);
        assertEquals("**Steve\\_Bob** deposited **5 Emeralds**", result.formattedMessage());
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
}
