package info.avicia.avoutils.features.anniparty;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnniRosterTest {

    @Test
    void getPartiesReturnsEmptyForNullList() {
        AnniRoster roster = new AnniRoster();
        roster.parties = null;
        assertTrue(roster.getParties().isEmpty());
    }

    @Test
    void findPartyContainingIsCaseInsensitive() {
        AnniRoster roster = twoPartyRoster();

        assertEquals(1L, roster.findPartyContaining("bob").partyId);
        assertEquals(2L, roster.findPartyContaining("CHARLIE").partyId);
        assertNull(roster.findPartyContaining("nobody"));
        assertNull(roster.findPartyContaining(null));
    }

    @Test
    void findLedPartyFindsLeaderOnly() {
        AnniRoster roster = twoPartyRoster();

        assertEquals(1L, roster.findLedParty("bob").partyId);
        assertEquals(2L, roster.findLedParty("charlie").partyId);
        assertNull(roster.findLedParty("alice")); // member but not leader
        assertNull(roster.findLedParty(null));
    }

    private static AnniRoster twoPartyRoster() {
        AnniPartyData party1 = new AnniPartyData();
        party1.partyId = 1;
        party1.members = new ArrayList<>(List.of(member("Alice", false), member("Bob", true)));

        AnniPartyData party2 = new AnniPartyData();
        party2.partyId = 2;
        party2.members = new ArrayList<>(List.of(member("Charlie", true)));

        AnniRoster roster = new AnniRoster();
        roster.parties = new ArrayList<>(List.of(party1, party2));
        return roster;
    }

    private static AnniMemberData member(String name, boolean isLeader) {
        AnniMemberData member = new AnniMemberData();
        member.name = name;
        member.isLeader = isLeader;
        return member;
    }
}
