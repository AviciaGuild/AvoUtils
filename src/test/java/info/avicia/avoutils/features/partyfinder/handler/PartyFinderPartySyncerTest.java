package info.avicia.avoutils.features.partyfinder.handler;

import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.features.partyfinder.api.ApiResponse;
import info.avicia.avoutils.features.partyfinder.api.PartyData;
import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import net.minecraft.client.MinecraftClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartyFinderPartySyncerTest {

    private PartyFinderClient apiClient;
    private PartyFinderPartySyncer syncer;

    @BeforeEach
    void setUp() {
        apiClient = mock(PartyFinderClient.class);
        syncer = new PartyFinderPartySyncer(apiClient);
    }

    @SuppressWarnings("unchecked")
    private Set<String> getKnownDiscordMembers() throws Exception {
        Field field = PartyFinderPartySyncer.class.getDeclaredField("knownDiscordMembers");
        field.setAccessible(true);
        return (Set<String>) field.get(syncer);
    }

    @SuppressWarnings("unchecked")
    private Set<String> getInGameSeenMembers() throws Exception {
        Field field = PartyFinderPartySyncer.class.getDeclaredField("inGameSeenMembers");
        field.setAccessible(true);
        return (Set<String>) field.get(syncer);
    }

    private void invokeOnPartyListParsed(List<String> members) throws Exception {
        Method method = PartyFinderPartySyncer.class.getDeclaredMethod("onPartyListParsed", List.class);
        method.setAccessible(true);
        method.invoke(syncer, members);
    }

    @Test
    void addKnownMembersIgnoresReservedPlaceholdersAndNulls() throws Exception {
        syncer.addKnownMembers(List.of("Alice", "<RESERVED>", "<reserved>", "<Reserved>", "   ", "<SLOT_1>", "Bob"));

        Set<String> known = getKnownDiscordMembers();
        assertEquals(3, known.size());
        assertTrue(known.contains("alice"));
        assertTrue(known.contains("bob"));
        assertTrue(known.contains("<slot_1>"));
        assertFalse(known.contains("<reserved>"));
    }

    @Test
    void onPartyListParsedSkipsWhenTrackedPartyIdNegative() throws Exception {
        // Tracked party ID is -1 (no active party tracked)
        assertEquals(-1, syncer.getTrackedPartyId());

        invokeOnPartyListParsed(List.of("Leader", "Alice"));

        // Verify no API calls are made
        verify(apiClient, never()).listParties();
        verify(apiClient, never()).reserveIngame(anyLong(), anyString());
        verify(apiClient, never()).kickMember(anyLong(), anyString());
    }

    @Test
    void onPartyListParsedNeverAutoKicksReservedPlaceholder() throws Exception {
        try (MockedStatic<MinecraftClient> mcStatic = mockStatic(MinecraftClient.class);
             MockedStatic<PlayerUtil> playerUtilStatic = mockStatic(PlayerUtil.class)) {

            mcStatic.when(MinecraftClient::getInstance).thenReturn(null);
            playerUtilStatic.when(() -> PlayerUtil.isSelf("Leader")).thenReturn(true);
            playerUtilStatic.when(() -> PlayerUtil.isSelf("Alice")).thenReturn(false);
            playerUtilStatic.when(() -> PlayerUtil.normalizeName(anyString())).thenCallRealMethod();
            playerUtilStatic.when(() -> PlayerUtil.namesEqual(anyString(), anyString())).thenCallRealMethod();

            syncer.setTrackedPartyId(999L);
            syncer.addKnownMembers(List.of("Alice", "<RESERVED>"));

            // In-game list only has Leader (Alice is not in-game yet, <RESERVED> is not in-game)
            invokeOnPartyListParsed(List.of("Leader"));

            // Verify <RESERVED> was NEVER kicked
            verify(apiClient, never()).kickMember(anyLong(), eq("<RESERVED>"));
            verify(apiClient, never()).kickMember(anyLong(), eq("<reserved>"));
            // Verify pending Discord member Alice is also NOT kicked
            verify(apiClient, never()).kickMember(anyLong(), eq("Alice"));
            verify(apiClient, never()).kickMember(anyLong(), eq("alice"));
        }
    }

    @Test
    void onPartyListParsedAutoKicksInGameMemberWhoLeaves() throws Exception {
        try (MockedStatic<MinecraftClient> mcStatic = mockStatic(MinecraftClient.class);
             MockedStatic<PlayerUtil> playerUtilStatic = mockStatic(PlayerUtil.class)) {

            mcStatic.when(MinecraftClient::getInstance).thenReturn(null);
            playerUtilStatic.when(() -> PlayerUtil.isSelf("Leader")).thenReturn(true);
            playerUtilStatic.when(() -> PlayerUtil.isSelf("Bob")).thenReturn(false);
            playerUtilStatic.when(() -> PlayerUtil.normalizeName(anyString())).thenCallRealMethod();
            playerUtilStatic.when(() -> PlayerUtil.namesEqual(anyString(), anyString())).thenCallRealMethod();

            syncer.setTrackedPartyId(999L);
            ApiResponse okResp = new ApiResponse();
            okResp.ok = true;
            when(apiClient.reserveIngame(anyLong(), anyString())).thenReturn(CompletableFuture.completedFuture(okResp));
            when(apiClient.kickMember(anyLong(), anyString())).thenReturn(CompletableFuture.completedFuture(okResp));

            // Bob was seen in the party
            invokeOnPartyListParsed(List.of("Leader", "Bob"));

            // Bob left in-game party, now only Leader
            invokeOnPartyListParsed(List.of("Leader"));

            // Verify Bob was kicked because he was an in-game member who is no longer in the party
            verify(apiClient).kickMember(999L, "bob");
        }
    }

    @Test
    void onPartyListParsedWithEmptyMembersDoesNotAutoKick() throws Exception {
        try (MockedStatic<MinecraftClient> mcStatic = mockStatic(MinecraftClient.class);
             MockedStatic<PlayerUtil> playerUtilStatic = mockStatic(PlayerUtil.class)) {

            mcStatic.when(MinecraftClient::getInstance).thenReturn(null);
            playerUtilStatic.when(() -> PlayerUtil.isSelf("Leader")).thenReturn(true);
            playerUtilStatic.when(() -> PlayerUtil.isSelf("Bob")).thenReturn(false);
            playerUtilStatic.when(() -> PlayerUtil.normalizeName(anyString())).thenCallRealMethod();
            playerUtilStatic.when(() -> PlayerUtil.namesEqual(anyString(), anyString())).thenCallRealMethod();

            syncer.setTrackedPartyId(999L);
            ApiResponse okResp = new ApiResponse();
            okResp.ok = true;
            when(apiClient.reserveIngame(anyLong(), anyString())).thenReturn(CompletableFuture.completedFuture(okResp));

            // Bob was seen in the party
            invokeOnPartyListParsed(List.of("Leader", "Bob"));

            // Dispatch empty member list (e.g. not in party or left party)
            invokeOnPartyListParsed(List.of());

            // Verify no kicks were issued
            verify(apiClient, never()).kickMember(anyLong(), anyString());
        }
    }

    @Test
    void onPartyListParsedKickingMemberRollsBackOnFailure() throws Exception {
        try (MockedStatic<MinecraftClient> mcStatic = mockStatic(MinecraftClient.class);
             MockedStatic<PlayerUtil> playerUtilStatic = mockStatic(PlayerUtil.class)) {

            mcStatic.when(MinecraftClient::getInstance).thenReturn(null);
            playerUtilStatic.when(() -> PlayerUtil.isSelf("Leader")).thenReturn(true);
            playerUtilStatic.when(() -> PlayerUtil.isSelf("Bob")).thenReturn(false);
            playerUtilStatic.when(() -> PlayerUtil.normalizeName(anyString())).thenCallRealMethod();
            playerUtilStatic.when(() -> PlayerUtil.namesEqual(anyString(), anyString())).thenCallRealMethod();

            syncer.setTrackedPartyId(999L);
            ApiResponse okResp = new ApiResponse();
            okResp.ok = true;
            when(apiClient.reserveIngame(anyLong(), anyString())).thenReturn(CompletableFuture.completedFuture(okResp));

            ApiResponse failResp = new ApiResponse();
            failResp.ok = false;
            when(apiClient.kickMember(anyLong(), anyString())).thenReturn(CompletableFuture.completedFuture(failResp));

            // Bob was seen in the party
            invokeOnPartyListParsed(List.of("Leader", "Bob"));

            // Bob left in-game party
            invokeOnPartyListParsed(List.of("Leader"));

            // Verify kick was attempted
            verify(apiClient).kickMember(999L, "bob");

            // Verify inGameSeenMembers was restored on failure
            Set<String> seen = getInGameSeenMembers();
            assertTrue(seen.contains("bob"));
        }
    }
}

