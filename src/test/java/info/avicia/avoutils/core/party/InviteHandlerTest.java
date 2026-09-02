package info.avicia.avoutils.core.party;

import info.avicia.avoutils.testutil.TestReflection;
import net.minecraft.client.MinecraftClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class InviteHandlerTest {

    private InviteHandler handler;

    @BeforeEach
    void setUp() {
        handler = new InviteHandler();
        TestReflection.set(InGamePartyTracker.getInstance(), "inParty", false);
    }

    @Test
    void inviteAllFiltersReservedSelfAndAlreadyInGame() {
        List<String> queued = handler.inviteAll(
                Arrays.asList(null, "", "<RESERVED>", "Self", "Other", "AlreadyIn"),
                "self",
                Set.of("alreadyin"));

        assertEquals(List.of("Other"), queued);
        assertTrue(handler.hasPending());
        assertEquals(2, handler.pendingCount()); // __CREATE__ + Other
    }

    @Test
    void inviteAllMatchingIsCaseInsensitive() {
        List<String> queued = handler.inviteAll(
                List.of("SELF", "ALREADYIN", "Other"),
                "self",
                Set.of("alreadyin"));

        assertEquals(List.of("Other"), queued);
    }

    @Test
    void queueInvitesPrependsPartyCreateWhenNotInParty() {
        handler.queueInvites(List.of("Bob", "Alice"));

        @SuppressWarnings("unchecked")
        Queue<String> queue = TestReflection.get(handler, "inviteQueue");

        assertEquals(List.of("__CREATE__", "Bob", "Alice"), List.copyOf(queue));
    }

    @Test
    void commandForBuildsCreateAndInviteCommands() {
        assertEquals("party create", InviteHandler.commandFor("__CREATE__"));
        assertEquals("party invite Bob", InviteHandler.commandFor("Bob"));
    }

    @Test
    void tickDoesNothingWhenPlayerIsNull() {
        MinecraftClient client = mock(MinecraftClient.class);

        handler.queueInvites(List.of("Bob"));
        handler.tick(client);

        assertEquals(2, handler.pendingCount()); // queue untouched
    }

    @Test
    void clearRemovesPendingInvites() {
        handler.queueInvites(List.of("Bob"));
        assertTrue(handler.hasPending());

        handler.clear();

        assertFalse(handler.hasPending());
        assertEquals(0, handler.pendingCount());
    }
}
