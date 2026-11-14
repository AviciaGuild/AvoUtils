package info.avicia.avoutils.features.chatbridge;

import info.avicia.avoutils.testutil.TextFixtures;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AllyDetectorTest {

    @Test
    void detectsIncomingAllyRequestWithHoverAndDirectUsername() {
        Component rawPacket = Component.empty()
                .append(TextFixtures.hoverText("Player", "Player's real username is Large_Mug"))
                .append(" from list is requesting to be allied");
        AllyDetector.Result res1 = AllyDetector.tryDetect("Player from list is requesting to be allied", rawPacket);
        assertNotNull(res1);
        assertEquals(AllyDetector.Action.INCOMING_REQUEST, res1.action());
        assertEquals("Large_Mug", res1.actor());
        assertEquals("list", res1.guild());

        AllyDetector.Result res2 = AllyDetector.tryDetect(
                "LargeMug from list is requesting to be allied", Component.literal(""));
        assertNotNull(res2);
        assertEquals(AllyDetector.Action.INCOMING_REQUEST, res2.action());
        assertEquals("LargeMug", res2.actor());
        assertEquals("list", res2.guild());
    }

    @Test
    void detectsOutgoingAllyRequest() {
        Component rawPacket = Component.empty()
                .append(TextFixtures.hoverText("Player", "Player's real username is CupBoi"))
                .append(" sent Silk Road a request to be allied");
        AllyDetector.Result res = AllyDetector.tryDetect(
                "Player sent Silk Road a request to be allied", rawPacket);
        assertNotNull(res);
        assertEquals(AllyDetector.Action.OUTGOING_REQUEST, res.action());
        assertEquals("CupBoi", res.actor());
        assertEquals("Silk Road", res.guild());
    }

    @Test
    void detectsTheirRejectionAndOurRejection() {
        AllyDetector.Result theirs = AllyDetector.tryDetect(
                "Silk Road rejected the alliance request", Component.literal(""));
        assertNotNull(theirs);
        assertEquals(AllyDetector.Action.THEIR_REJECT, theirs.action());
        assertNull(theirs.actor());
        assertEquals("Silk Road", theirs.guild());

        Component oursComp = Component.empty()
                .append(TextFixtures.hoverText("avo ignis war dps", "'s real name is CupBoi"))
                .append(" rejected list alliance request");
        AllyDetector.Result ours = AllyDetector.tryDetect(
                "avo ignis war dps rejected list alliance request", oursComp);
        assertNotNull(ours);
        assertEquals(AllyDetector.Action.OUR_REJECT, ours.action());
        assertEquals("CupBoi", ours.actor());
        assertEquals("list", ours.guild());
    }

    @Test
    void detectsWithdrawnAllianceRequests() {
        AllyDetector.Result ours = AllyDetector.tryDetect(
                "CupBoi withdrew Another Guild alliance request", Component.literal(""));
        assertNotNull(ours);
        assertEquals(AllyDetector.Action.OUR_WITHDRAW, ours.action());
        assertEquals("CupBoi", ours.actor());
        assertEquals("Another Guild", ours.guild());

        AllyDetector.Result theirs = AllyDetector.tryDetect(
                "Another Guild withdrew the alliance request", Component.literal(""));
        assertNotNull(theirs);
        assertEquals(AllyDetector.Action.THEIR_WITHDRAW, theirs.action());
        assertNull(theirs.actor());
        assertEquals("Another Guild", theirs.guild());
    }

    @Test
    void detectsAllianceFormedOnlyWhenInvolvingAvicia() {
        AllyDetector.Result formed1 = AllyDetector.tryDetect(
                "Avicia formed an alliance with Silk Road", Component.literal(""));
        assertNotNull(formed1);
        assertEquals(AllyDetector.Action.OUR_FORM, formed1.action());
        assertNull(formed1.actor());
        assertEquals("Silk Road", formed1.guild());

        AllyDetector.Result formed2 = AllyDetector.tryDetect(
                "Silk Road formed an alliance with Avicia", Component.literal(""));
        assertNotNull(formed2);
        assertEquals(AllyDetector.Action.THEIR_FORM, formed2.action());
        assertNull(formed2.actor());
        assertEquals("Silk Road", formed2.guild());

        assertNull(AllyDetector.tryDetect(
                "Tannslee formed an alliance with Radiant Roses", Component.literal("")));
    }

    @Test
    void detectsAllianceRevokedFromBothDirections() {
        AllyDetector.Result otherRevoked = AllyDetector.tryDetect(
                "Anime Lovers revoked the alliance with Avicia", Component.literal(""));
        assertNotNull(otherRevoked);
        assertEquals(AllyDetector.Action.THEIR_REVOKE, otherRevoked.action());
        assertNull(otherRevoked.actor());
        assertEquals("Anime Lovers", otherRevoked.guild());

        AllyDetector.Result ourRevoked = AllyDetector.tryDetect(
                "GaztheCat revoked the alliance with Chiefs Of Corkus", Component.literal(""));
        assertNotNull(ourRevoked);
        assertEquals(AllyDetector.Action.OUR_REVOKE, ourRevoked.action());
        assertEquals("GaztheCat", ourRevoked.actor());
        assertEquals("Chiefs Of Corkus", ourRevoked.guild());
    }

    @Test
    void rejectsPlayerAuthoredChatSpoofing() {
        assertNull(AllyDetector.tryDetect(
                "Frieren: Avicia formed an alliance with Silk Road", Component.literal("")));
        assertNull(AllyDetector.tryDetect(
                "Frieren -> You: Silk Road rejected the alliance request", Component.literal("")));
    }
}
