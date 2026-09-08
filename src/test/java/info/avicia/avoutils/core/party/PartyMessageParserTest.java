package info.avicia.avoutils.core.party;

import info.avicia.avoutils.testutil.TextFixtures;
import net.minecraft.text.Text;
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
        assertEquals("Steve", result.player());
    }

    @Test
    void parseDetectsJoinWithColorCodes() {
        PartyMessageParser.Result result =
                PartyMessageParser.parse("§eSteve has joined your party, say hello!");
        assertEquals(PartyMessageParser.Event.JOIN, result.event());
        assertEquals("Steve", result.player());
    }

    @Test
    void parseDetectsKick() {
        PartyMessageParser.Result result =
                PartyMessageParser.parse("Steve has been kicked from the party!");
        assertEquals(PartyMessageParser.Event.KICK, result.event());
        assertEquals("Steve", result.player());
    }

    @Test
    void parseDetectsKickWithFontTags() {
        PartyMessageParser.Result join =
                PartyMessageParser.parse("&e&{fr:cp}&{fr:d} LargeMug has joined your party, say hello!");
        org.junit.jupiter.api.Assertions.assertNotNull(join);
        assertEquals(PartyMessageParser.Event.JOIN, join.event());
        assertEquals("LargeMug", join.player());

        PartyMessageParser.Result kick =
                PartyMessageParser.parse("&e&{fr:cp}&{fr:d} LargeMug has been kicked from the party!");
        org.junit.jupiter.api.Assertions.assertNotNull(kick);
        assertEquals(PartyMessageParser.Event.KICK, kick.event());
        assertEquals("LargeMug", kick.player());
    }

    @Test
    void parseDetectsLeave() {
        PartyMessageParser.Result result =
                PartyMessageParser.parse("Steve has left the party!");
        assertEquals(PartyMessageParser.Event.LEAVE, result.event());
        assertEquals("Steve", result.player());
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
        assertNull(PartyMessageParser.parse("Steve: LargeMug has been kicked from the party!"));
    }

    @Test
    void parseDetectsExactSelfLeave() {
        PartyMessageParser.Result result =
                PartyMessageParser.parse("You have left your current party");
        org.junit.jupiter.api.Assertions.assertNotNull(result);
        assertEquals(PartyMessageParser.Event.LEFT_PARTY, result.event());
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

    @Test
    void parseHandlesRawWynncraftChatWithPrivateUseAndFontTags() {
        PartyMessageParser.Result listResult =
                PartyMessageParser.parse("&e&{fr:cp}󏿼&{fr:d} Party members: &bCupBoi, and &fLargeMug");
        org.junit.jupiter.api.Assertions.assertNotNull(listResult);
        assertEquals(PartyMessageParser.Event.PARTY_LIST, listResult.event());
        assertEquals(List.of("CupBoi", "LargeMug"), listResult.partyListMembers());

        PartyMessageParser.Result joinResult =
                PartyMessageParser.parse("󏿼󐀆 §eLargeMug has joined your party, say hello!");
        org.junit.jupiter.api.Assertions.assertNotNull(joinResult);
        assertEquals(PartyMessageParser.Event.JOIN, joinResult.event());
        assertEquals("LargeMug", joinResult.player());
    }

    @Test
    void parseDetectsPartyListThreeMembersWithOxfordComma() {
        PartyMessageParser.Result result =
                PartyMessageParser.parse("Party members: CupBoi, LargeMug, and SomeoneElse");
        org.junit.jupiter.api.Assertions.assertNotNull(result);
        assertEquals(PartyMessageParser.Event.PARTY_LIST, result.event());
        assertEquals(List.of("CupBoi", "LargeMug", "SomeoneElse"), result.partyListMembers());
    }

    @Test
    void parseRejectsForgedChannelAndRankMessages() {
        assertNull(PartyMessageParser.parse("[VIP] Steve: You have left your current party"));
        assertNull(PartyMessageParser.parse("[Guild] Steve: Party members: Alex, Bob"));
        assertNull(PartyMessageParser.parse("[Party] [Champion] Steve: Your party has been disbanded."));
        assertNull(PartyMessageParser.parse("Steve whispers: LargeMug has been kicked from the party!"));
    }

    @Test
    void parseRejectsTrailingGarbage() {
        assertNull(PartyMessageParser.parse("LargeMug has joined your party, say hello! haha"));
        assertNull(PartyMessageParser.parse("LargeMug has been kicked from the party! lol"));
        assertNull(PartyMessageParser.parse("You have left your current party extra"));
    }

    @Test
    void parseDetectsKickWithClassNickname() {
        PartyMessageParser.Result result =
                PartyMessageParser.parse("avo ignis war dps has been kicked from the party!");
        org.junit.jupiter.api.Assertions.assertNotNull(result);
        assertEquals(PartyMessageParser.Event.KICK, result.event());
        assertEquals("avo ignis war dps", result.player());
    }

    @Test
    void parseDetectsKickWithClassNicknameAndResolvesHoverRealName() {
        Text message = TextFixtures.hoverText(
                "avo ignis war dps has been kicked from the party!",
                "'s real name is CupBoi");
        PartyMessageParser.Result result =
                PartyMessageParser.parse("avo ignis war dps has been kicked from the party!", message);
        org.junit.jupiter.api.Assertions.assertNotNull(result);
        assertEquals(PartyMessageParser.Event.KICK, result.event());
        assertEquals("CupBoi", result.player());
    }

    @Test
    void parseDetectsJoinAndLeaveVariants() {
        PartyMessageParser.Result join2 = PartyMessageParser.parse("CupBoi has joined the party!");
        org.junit.jupiter.api.Assertions.assertNotNull(join2);
        assertEquals(PartyMessageParser.Event.JOIN, join2.event());
        assertEquals("CupBoi", join2.player());

        PartyMessageParser.Result leavePeriod = PartyMessageParser.parse("CupBoi has left the party.");
        org.junit.jupiter.api.Assertions.assertNotNull(leavePeriod);
        assertEquals(PartyMessageParser.Event.LEAVE, leavePeriod.event());
        assertEquals("CupBoi", leavePeriod.player());
    }
}
