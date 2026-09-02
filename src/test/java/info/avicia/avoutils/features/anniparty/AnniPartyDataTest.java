package info.avicia.avoutils.features.anniparty;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnniPartyDataTest {

    @Test
    void getServerDisplayFallsBackToQuestionMarks() {
        AnniPartyData party = new AnniPartyData();
        assertEquals("???", party.getServerDisplay());

        party.server = "   ";
        assertEquals("???", party.getServerDisplay());
    }

    @Test
    void getServerDisplayReturnsServerName() {
        AnniPartyData party = new AnniPartyData();
        party.server = "WC1";
        assertEquals("WC1", party.getServerDisplay());
    }

    @Test
    void getMembersReturnsEmptyForNullList() {
        AnniPartyData party = new AnniPartyData();
        party.members = null;
        assertTrue(party.getMembers().isEmpty());
    }
}
