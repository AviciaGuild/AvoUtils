package info.avicia.avoutils.features.anniparty;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.testutil.TestReflection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AnniPartyFeatureTest {

    @BeforeEach
    void setUp() {
        WynncraftServerPolicy.setScopeOverride(() -> WynncraftServerPolicy.Scope.MAIN);
    }

    @AfterEach
    void tearDown() {
        WynncraftServerPolicy.setScopeOverride(null);
    }

    private void withMockedAuth(boolean guildMember, Consumer<AnniPartyFeature> testBody) {
        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            when(authService.isGuildMember()).thenReturn(guildMember);
            testBody.accept(new AnniPartyFeature());
        }
    }

    @Test
    void onRosterEventParsesAndNotifiesListeners() {
        withMockedAuth(true, feature -> {
            AtomicReference<AnniRoster> received = new AtomicReference<>();
            feature.addRosterListener(received::set);

            JsonObject json = JsonParser.parseString(
                    "{\"active\":true,\"parties\":[{\"party_id\":7,\"server\":\"WC1\","
                            + "\"members\":[{\"name\":\"Steve\",\"is_leader\":true}]}]}"
            ).getAsJsonObject();
            invokeOnRosterEvent(feature, json);

            assertTrue(feature.isActive());
            assertEquals(7L, received.get().findPartyContaining("steve").partyId);
        });
    }

    @Test
    void onRosterEventIgnoresNonGuildMembers() {
        withMockedAuth(false, feature -> {
            invokeOnRosterEvent(feature, JsonParser.parseString("{\"active\":true}").getAsJsonObject());
            assertFalse(feature.isActive());
        });
    }

    @Test
    void onServerScopeChangedResetsRosterWhenNotMain() {
        AnniPartyFeature feature = new AnniPartyFeature();
        TestReflection.set(feature, "active", true);

        feature.onServerScopeChanged(WynncraftServerPolicy.Scope.BETA);
        assertFalse(feature.isActive());

        TestReflection.set(feature, "active", true);
        feature.onServerScopeChanged(WynncraftServerPolicy.Scope.BLOCKED);
        assertFalse(feature.isActive());

        TestReflection.set(feature, "active", true);
        feature.onServerScopeChanged(WynncraftServerPolicy.Scope.MAIN);
        assertTrue(feature.isActive());
    }

    @Test
    void getLedPartyUsesLocalPlayerName() {
        try (MockedStatic<PlayerUtil> player = mockStatic(PlayerUtil.class)) {
            player.when(PlayerUtil::selfName).thenReturn("Steve");

            AnniPartyFeature feature = new AnniPartyFeature();
            TestReflection.set(feature, "roster", rosterWithLeader("Steve", 3L));
            TestReflection.set(feature, "active", true);

            assertEquals(3L, feature.getLedParty().partyId);
        }
    }

    @Test
    void removeRosterListenerStopsNotifications() {
        withMockedAuth(true, feature -> {
            AtomicReference<AnniRoster> received = new AtomicReference<>();
            Consumer<AnniRoster> listener = received::set;
            feature.addRosterListener(listener);
            feature.removeRosterListener(listener);

            invokeOnRosterEvent(feature, JsonParser.parseString("{\"active\":true}").getAsJsonObject());

            assertNull(received.get());
        });
    }

    private static AnniRoster rosterWithLeader(String name, long partyId) {
        AnniMemberData leader = new AnniMemberData();
        leader.name = name;
        leader.isLeader = true;

        AnniPartyData party = new AnniPartyData();
        party.partyId = partyId;
        party.members = new ArrayList<>(List.of(leader));

        AnniRoster roster = new AnniRoster();
        roster.parties = new ArrayList<>(List.of(party));
        return roster;
    }

    private static void invokeOnRosterEvent(AnniPartyFeature feature, JsonObject json) {
        TestReflection.invoke(feature, "onRosterEvent", new Class[]{JsonObject.class}, json);
    }
}
