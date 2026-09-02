package info.avicia.avoutils.core.party;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyMessageParserTest {

    @Test
    void parseReturnsNullForNullOrIrrelevantText() {
        assertNull(PartyMessageParser.parse(null));
        assertNull(PartyMessageParser.parse("hello world"));
        assertNull(PartyMessageParser.parse(""));
    }

    @Test
    void parseDetectsJoin() {
        PartyMessageParser.Result result =
                PartyMessageParser.parse("Steve has joined your party, say hello!");
        assertEquals(PartyMessageParser.Event.JOIN, result.event());
    }

    @Test
    void parseDetectsJoinWithRankPrefixAndColorCodes() {
        PartyMessageParser.Result result =
                PartyMessageParser.parse("§a[MVP++] Steve has joined your party, say hello!");
        assertEquals(PartyMessageParser.Event.JOIN, result.event());
    }

    @Test
    void parseDetectsKick() {
        PartyMessageParser.Result result =
                PartyMessageParser.parse("Steve has been kicked from the party!");
        assertEquals(PartyMessageParser.Event.KICK, result.event());
    }

    @Test
    void parseDetectsLeave() {
        PartyMessageParser.Result result =
                PartyMessageParser.parse("Steve has left the party!");
        assertEquals(PartyMessageParser.Event.LEAVE, result.event());
    }

    @Test
    void parseDetectsPartyListWithMembers() {
        PartyMessageParser.Result result =
                PartyMessageParser.parse("Party members: Steve, Alex, and Bob");
        assertEquals(PartyMessageParser.Event.PARTY_LIST, result.event());
        assertEquals(List.of("Steve", "Alex", "Bob"), result.partyListMembers());
    }

    @Test
    void parseDetectsPartyListWithSingleMember() {
        PartyMessageParser.Result result = PartyMessageParser.parse("Party members: Steve");
        assertEquals(PartyMessageParser.Event.PARTY_LIST, result.event());
        assertEquals(List.of("Steve"), result.partyListMembers());
    }

    @Test
    void parseDetectsSystemPartyEvents() {
        assertEquals(PartyMessageParser.Event.NOT_IN_PARTY,
                PartyMessageParser.parse("You must be in a party to do that!").event());
        assertEquals(PartyMessageParser.Event.KICKED_FROM_PARTY,
                PartyMessageParser.parse("You have been kicked from your party!").event());
        assertEquals(PartyMessageParser.Event.LEFT_PARTY,
                PartyMessageParser.parse("You have left your current party!").event());
        assertEquals(PartyMessageParser.Event.DISBANDED,
                PartyMessageParser.parse("Your party has been disbanded.").event());
    }

    @Test
    void parseRejectsForgedPlayerMessagesWithColon() {
        assertNull(PartyMessageParser.parse("Steve: has joined your party, say hello!"));
    }

    @Test
    void parseRequiresFullJoinPattern() {
        assertNull(PartyMessageParser.parse("Steve has joined your party"));
    }

    @Test
    void systemEventResultsHaveNoMembers() {
        PartyMessageParser.Result result =
                PartyMessageParser.parse("Your party has been disbanded.");
        assertTrue(result.partyListMembers().isEmpty());
    }
}
