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
                .append(TextFixtures.hoverText("Player", "Player's real username is LargeMug"))
                .append(" from list is requesting to be allied");
        AllyDetector.Result res1 = AllyDetector.tryDetect("Player from list is requesting to be allied", rawPacket, null);
        assertNotNull(res1);
        assertEquals("**LargeMug** from **list** is requesting to be allied", res1.formattedMessage());

        AllyDetector.Result res2 = AllyDetector.tryDetect(
                "LargeMug from list is requesting to be allied", Component.literal(""), null);
        assertNotNull(res2);
        assertEquals("**LargeMug** from **list** is requesting to be allied", res2.formattedMessage());
    }

    @Test
    void detectsOutgoingAllyRequest() {
        Component rawPacket = Component.empty()
                .append(TextFixtures.hoverText("Player", "Player's real username is CupBoi"))
                .append(" sent Silk Road a request to be allied");
        AllyDetector.Result res = AllyDetector.tryDetect(
                "Player sent Silk Road a request to be allied", rawPacket, null);
        assertNotNull(res);
        assertEquals("**CupBoi** sent **Silk Road** a request to be allied", res.formattedMessage());
    }

    @Test
    void detectsTheirRejectionAndOurRejection() {
        AllyDetector.Result theirs = AllyDetector.tryDetect(
                "Silk Road rejected the alliance request", Component.literal(""), null);
        assertNotNull(theirs);
        assertEquals("**Silk Road** rejected the alliance request", theirs.formattedMessage());

        Component oursComp = Component.empty()
                .append(TextFixtures.hoverText("avo ignis war dps", "'s real name is CupBoi"))
                .append(" rejected list alliance request");
        AllyDetector.Result ours = AllyDetector.tryDetect(
                "avo ignis war dps rejected list alliance request", oursComp, null);
        assertNotNull(ours);
        assertEquals("**CupBoi** rejected **list**'s alliance request", ours.formattedMessage());
    }

    @Test
    void detectsWithdrawnAllianceRequests() {
        AllyDetector.Result ours = AllyDetector.tryDetect(
                "CupBoi withdrew Another Guild alliance request", Component.literal(""), null);
        assertNotNull(ours);
        assertEquals("**CupBoi** withdrew **Another Guild**'s alliance request", ours.formattedMessage());

        AllyDetector.Result theirs = AllyDetector.tryDetect(
                "Another Guild withdrew the alliance request", Component.literal(""), null);
        assertNotNull(theirs);
        assertEquals("**Another Guild** withdrew the alliance request", theirs.formattedMessage());
    }

    @Test
    void detectsAllianceFormedOnlyWhenInvolvingAvicia() {
        AllyDetector.Result formed1 = AllyDetector.tryDetect(
                "Avicia formed an alliance with Silk Road", Component.literal(""), null);
        assertNotNull(formed1);
        assertEquals("**Avicia** formed an alliance with **Silk Road**", formed1.formattedMessage());

        AllyDetector.Result formed2 = AllyDetector.tryDetect(
                "Silk Road formed an alliance with Avicia", Component.literal(""), null);
        assertNotNull(formed2);
        assertEquals("**Silk Road** formed an alliance with **Avicia**", formed2.formattedMessage());

        assertNull(AllyDetector.tryDetect(
                "Tannslee formed an alliance with Radiant Roses", Component.literal(""), null));
    }

    @Test
    void detectsAllianceRevokedFromBothDirections() {
        AllyDetector.Result otherRevoked = AllyDetector.tryDetect(
                "Anime Lovers revoked the alliance with Avicia", Component.literal(""), null);
        assertNotNull(otherRevoked);
        assertEquals("**Anime Lovers** revoked the alliance with **Avicia**", otherRevoked.formattedMessage());

        AllyDetector.Result ourRevoked = AllyDetector.tryDetect(
                "GaztheCat revoked the alliance with Chiefs Of Corkus", Component.literal(""), null);
        assertNotNull(ourRevoked);
        assertEquals("**GaztheCat** revoked the alliance with **Chiefs Of Corkus**", ourRevoked.formattedMessage());
    }

    @Test
    void rejectsPlayerAuthoredChatSpoofing() {
        assertNull(AllyDetector.tryDetect(
                "Frieren: Avicia formed an alliance with Silk Road", Component.literal(""), null));
        assertNull(AllyDetector.tryDetect(
                "Frieren -> You: Silk Road rejected the alliance request", Component.literal(""), null));
    }
}
