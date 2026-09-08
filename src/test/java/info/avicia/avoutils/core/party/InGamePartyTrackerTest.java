package info.avicia.avoutils.core.party;

import info.avicia.avoutils.testutil.TestReflection;
import info.avicia.avoutils.testutil.TextFixtures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class InGamePartyTrackerTest {

    private InGamePartyTracker tracker;

    @BeforeEach
    void resetTrackerState() {
        tracker = InGamePartyTracker.getInstance();
        TestReflection.set(tracker, "inParty", false);
        TestReflection.set(tracker, "hiddenPartyListExpireTime", 0L);
        TestReflection.set(tracker, "lastTriggerTime", 0L);
        Set<String> members = TestReflection.get(tracker, "lastPartyListMembers");
        members.clear();
    }

    @Test
    void nonPartyMessageReturnsFalse() {
        assertFalse(tracker.onChatMessage("hello world"));
        assertFalse(tracker.isInParty());
    }

    @Test
    void partyListSetsInPartyAndMembers() {
        assertFalse(tracker.onChatMessage("Party members: Steve, Alex, and Bob"));
        assertTrue(tracker.isInParty());
        assertEquals(Set.of("Steve", "Alex", "Bob"), tracker.getLastPartyListMembers());
    }

    @Test
    void disbandedClearsPartyState() {
        tracker.onChatMessage("Party members: Steve");
        assertTrue(tracker.isInParty());

        tracker.onChatMessage("Your party has been disbanded.");
        assertFalse(tracker.isInParty());
        assertTrue(tracker.getLastPartyListMembers().isEmpty());
    }

    @Test
    void joinMarksInPartyAndRequestsPartyList() {
        try (MockedStatic<MinecraftClient> mc = mockStatic(MinecraftClient.class)) {
            MinecraftClient client = mock(MinecraftClient.class);
            mc.when(MinecraftClient::getInstance).thenReturn(client);

            assertFalse(tracker.onChatMessage("Steve has joined your party, say hello!"));
            assertTrue(tracker.isInParty());
            assertTrue(tracker.getLastPartyListMembers().contains("Steve"));
        }
    }

    @Test
    void kickRemovesMemberImmediately() {
        try (MockedStatic<MinecraftClient> mc = mockStatic(MinecraftClient.class)) {
            MinecraftClient client = mock(MinecraftClient.class);
            mc.when(MinecraftClient::getInstance).thenReturn(client);

            tracker.onChatMessage("Party members: CupBoi, and LargeMug");
            assertEquals(Set.of("CupBoi", "LargeMug"), tracker.getLastPartyListMembers());

            tracker.onChatMessage("LargeMug has been kicked from the party!");
            assertEquals(Set.of("CupBoi"), tracker.getLastPartyListMembers());
        }
    }

    @Test
    void leaveRemovesMemberImmediately() {
        try (MockedStatic<MinecraftClient> mc = mockStatic(MinecraftClient.class)) {
            MinecraftClient client = mock(MinecraftClient.class);
            mc.when(MinecraftClient::getInstance).thenReturn(client);

            tracker.onChatMessage("Party members: CupBoi, and LargeMug");
            assertEquals(Set.of("CupBoi", "LargeMug"), tracker.getLastPartyListMembers());

            tracker.onChatMessage("LargeMug has left the party!");
            assertEquals(Set.of("CupBoi"), tracker.getLastPartyListMembers());
        }
    }

    @Test
    void triggerPartyListDebouncesRapidCalls() {
        try (MockedStatic<MinecraftClient> mc = mockStatic(MinecraftClient.class)) {
            MinecraftClient client = mock(MinecraftClient.class);
            mc.when(MinecraftClient::getInstance).thenReturn(client);
            org.mockito.Mockito.doAnswer(invocation -> {
                ((Runnable) invocation.getArgument(0)).run();
                return null;
            }).when(client).execute(org.mockito.ArgumentMatchers.any(Runnable.class));

            tracker.triggerPartyList();
            long firstTrigger = TestReflection.get(tracker, "lastTriggerTime");
            assertTrue(firstTrigger > 0);

            tracker.triggerPartyList();
            long secondTrigger = TestReflection.get(tracker, "lastTriggerTime");
            assertEquals(firstTrigger, secondTrigger);
        }
    }

    @Test
    void kickWithClassNicknameAndHoverRemovesRealMember() {
        try (MockedStatic<MinecraftClient> mc = mockStatic(MinecraftClient.class)) {
            MinecraftClient client = mock(MinecraftClient.class);
            mc.when(MinecraftClient::getInstance).thenReturn(client);

            tracker.onChatMessage("Party members: CupBoi, and LargeMug");
            assertEquals(Set.of("CupBoi", "LargeMug"), tracker.getLastPartyListMembers());

            Text message = TextFixtures.hoverText(
                    "avo ignis war dps has been kicked from the party!",
                    "'s real name is CupBoi");
            tracker.onChatMessage(message);
            assertEquals(Set.of("LargeMug"), tracker.getLastPartyListMembers());
        }
    }

    @Test
    void kickWithClassNicknameTriggersSyncEvenWithoutHover() {
        try (MockedStatic<MinecraftClient> mc = mockStatic(MinecraftClient.class)) {
            MinecraftClient client = mock(MinecraftClient.class);
            mc.when(MinecraftClient::getInstance).thenReturn(client);
            org.mockito.Mockito.doAnswer(invocation -> {
                ((Runnable) invocation.getArgument(0)).run();
                return null;
            }).when(client).execute(org.mockito.ArgumentMatchers.any(Runnable.class));

            tracker.onChatMessage("avo ignis war dps has been kicked from the party!");
            long triggerTime = TestReflection.get(tracker, "lastTriggerTime");
            assertTrue(triggerTime > 0);
        }
    }
}
