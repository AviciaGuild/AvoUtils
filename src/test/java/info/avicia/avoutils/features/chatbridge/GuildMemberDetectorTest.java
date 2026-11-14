package info.avicia.avoutils.features.chatbridge;

import info.avicia.avoutils.testutil.TextFixtures;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GuildMemberDetectorTest {

    private static final int GUILD_COLOR = 0x55FFFF;

    private static Component guildComponent(String text) {
        return TextFixtures.coloredText(text, GUILD_COLOR);
    }

    @Test
    void detectsPromotionsAndDemotionsAndIgnoresNoChange() {
        Component promoComp = Component.empty()
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(GUILD_COLOR)))
                .append(TextFixtures.hoverText("hardcoremaxxer", "hardcoremaxxer's real username is ActualAccount"))
                .append(" has set ")
                .append(TextFixtures.hoverText("Target Nickname", "Target Nickname's real username is NotReyz"))
                .append(" guild rank from Recruiter to Captain");

        GuildMemberDetector.Result promo = GuildMemberDetector.tryDetect(
                "hardcoremaxxer has set Target Nickname guild rank from Recruiter to Captain",
                promoComp);
        assertNotNull(promo);
        assertEquals(GuildMemberDetector.Action.PROMOTE, promo.action());
        assertEquals("ActualAccount", promo.actor());
        assertEquals("NotReyz", promo.target());
        assertEquals("Recruiter", promo.oldRank());
        assertEquals("Captain", promo.newRank());

        GuildMemberDetector.Result demote = GuildMemberDetector.tryDetect(
                "ActualAccount has set NotReyz guild rank from Chief to Recruit.",
                guildComponent("ActualAccount has set NotReyz guild rank from Chief to Recruit."));
        assertNotNull(demote);
        assertEquals(GuildMemberDetector.Action.DEMOTE, demote.action());
        assertEquals("ActualAccount", demote.actor());
        assertEquals("NotReyz", demote.target());
        assertEquals("Chief", demote.oldRank());
        assertEquals("Recruit", demote.newRank());

        assertNull(GuildMemberDetector.tryDetect(
                "ActualAccount has set NotReyz guild rank from Strategist to Strategist",
                guildComponent("ActualAccount has set NotReyz guild rank from Strategist to Strategist")));
    }

    @Test
    void detectsJoinLeaveAndKickWithRawUnescapedIgn() {
        GuildMemberDetector.Result join = GuildMemberDetector.tryDetect(
                "New_Member has joined the guild, say hello!",
                guildComponent("New_Member has joined the guild, say hello!"));
        assertNotNull(join);
        assertEquals(GuildMemberDetector.Action.JOIN, join.action());
        assertNull(join.actor());
        assertEquals("New_Member", join.target());

        GuildMemberDetector.Result leave = GuildMemberDetector.tryDetect(
                "OldMember has left the guild.",
                guildComponent("OldMember has left the guild."));
        assertNotNull(leave);
        assertEquals(GuildMemberDetector.Action.LEAVE, leave.action());
        assertEquals("OldMember", leave.target());

        Component kickComp = Component.empty()
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(GUILD_COLOR)))
                .append(TextFixtures.hoverText("catboy", "catboy's real username is FadeDave"))
                .append(" has kicked Slacker from the guild");
        GuildMemberDetector.Result kick = GuildMemberDetector.tryDetect(
                "catboy has kicked Slacker from the guild", kickComp);
        assertNotNull(kick);
        assertEquals(GuildMemberDetector.Action.KICK, kick.action());
        assertEquals("FadeDave", kick.actor());
        assertEquals("Slacker", kick.target());

        GuildMemberDetector.Result passiveKick = GuildMemberDetector.tryDetect(
                "Slacker has been kicked from the guild by FadeDave.",
                guildComponent("Slacker has been kicked from the guild by FadeDave."));
        assertNotNull(passiveKick);
        assertEquals(GuildMemberDetector.Action.KICK, passiveKick.action());
        assertEquals("FadeDave", passiveKick.actor());
        assertEquals("Slacker", passiveKick.target());
    }

    @Test
    void detectsInviteAndUninviteVariants() {
        GuildMemberDetector.Result inv1 = GuildMemberDetector.tryDetect(
                "Officer has invited Recruit to the guild",
                guildComponent("Officer has invited Recruit to the guild"));
        assertNotNull(inv1);
        assertEquals(GuildMemberDetector.Action.INVITE, inv1.action());
        assertEquals("Officer", inv1.actor());
        assertEquals("Recruit", inv1.target());

        Component inv2Comp = Component.empty()
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(GUILD_COLOR)))
                .append(TextFixtures.hoverText("Commander Lilacs", "Commander Lilacs's real username is RealLilacs"))
                .append(" invited NewMember to your guild!");
        GuildMemberDetector.Result inv2 = GuildMemberDetector.tryDetect(
                "Commander Lilacs invited NewMember to your guild!", inv2Comp);
        assertNotNull(inv2);
        assertEquals(GuildMemberDetector.Action.INVITE, inv2.action());
        assertEquals("RealLilacs", inv2.actor());
        assertEquals("NewMember", inv2.target());

        GuildMemberDetector.Result uninv = GuildMemberDetector.tryDetect(
                "GaztheCat uninvited NewMember from the guild",
                guildComponent("GaztheCat uninvited NewMember from the guild"));
        assertNotNull(uninv);
        assertEquals(GuildMemberDetector.Action.UNINVITE, uninv.action());
        assertEquals("GaztheCat", uninv.actor());
        assertEquals("NewMember", uninv.target());
    }

    @Test
    void rejectsPlayerChatAndUnresolvableNickname() {
        assertNull(GuildMemberDetector.tryDetect(
                "FadeDave: did anyone see who left the guild",
                guildComponent("FadeDave: did anyone see who left the guild")));
        assertNull(GuildMemberDetector.tryDetect(
                "unresolvable nickname invited NewMember to the guild",
                Component.literal("unresolvable nickname invited NewMember to the guild")));
    }
}
