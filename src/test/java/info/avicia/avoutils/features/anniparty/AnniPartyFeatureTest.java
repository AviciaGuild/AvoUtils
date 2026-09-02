package info.avicia.avoutils.features.anniparty;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.util.PlayerUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

    @Test
    void onRosterEventParsesAndNotifiesListeners() {
        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            when(authService.isGuildMember()).thenReturn(true);

            AnniPartyFeature feature = new AnniPartyFeature();
            AtomicReference<AnniRoster> received = new AtomicReference<>();
            feature.addRosterListener(received::set);

            JsonObject json = JsonParser.parseString(
                    "{\"active\":true,\"parties\":[{\"party_id\":7,\"server\":\"WC1\","
                            + "\"members\":[{\"name\":\"Steve\",\"is_leader\":true}]}]}"
            ).getAsJsonObject();
            invokeOnRosterEvent(feature, json);

            assertTrue(feature.isActive());
            assertEquals(7L, received.get().findPartyContaining("steve").partyId);
        }
    }

    @Test
    void onRosterEventIgnoresNonGuildMembers() {
        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            when(authService.isGuildMember()).thenReturn(false);

            AnniPartyFeature feature = new AnniPartyFeature();
            invokeOnRosterEvent(feature, JsonParser.parseString("{\"active\":true}").getAsJsonObject());

            assertFalse(feature.isActive());
        }
    }

    @Test
    void getLedPartyUsesLocalPlayerName() {
        try (MockedStatic<PlayerUtil> player = mockStatic(PlayerUtil.class)) {
            player.when(PlayerUtil::selfName).thenReturn("Steve");

            AnniPartyFeature feature = new AnniPartyFeature();
            setField(feature, "roster", rosterWithLeader("Steve", 3L));
            setField(feature, "active", true);

            assertEquals(3L, feature.getLedParty().partyId);
        }
    }

    @Test
    void removeRosterListenerStopsNotifications() {
        AnniPartyFeature feature = new AnniPartyFeature();
        AtomicReference<AnniRoster> received = new AtomicReference<>();
        Consumer<AnniRoster> listener = received::set;
        feature.addRosterListener(listener);
        feature.removeRosterListener(listener);

        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            when(authService.isGuildMember()).thenReturn(true);

            invokeOnRosterEvent(feature, JsonParser.parseString("{\"active\":true}").getAsJsonObject());

            assertNull(received.get());
        }
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
        try {
            Method method = AnniPartyFeature.class.getDeclaredMethod("onRosterEvent", JsonObject.class);
            method.setAccessible(true);
            method.invoke(feature, json);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke onRosterEvent", e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = AnniPartyFeature.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set field " + name, e);
        }
    }
}
