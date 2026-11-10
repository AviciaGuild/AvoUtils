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
                promoComp, "Observer");
        assertNotNull(promo);
        assertEquals("**ActualAccount** promoted **NotReyz** from **Recruiter** to **Captain**",
                promo.formattedMessage());

        GuildMemberDetector.Result demote = GuildMemberDetector.tryDetect(
                "[Guild] You has set NotReyz guild rank from Chief to Recruit.",
                Component.literal("[Guild] You has set NotReyz guild rank from Chief to Recruit."),
                "ActualAccount");
        assertNotNull(demote);
        assertEquals("**ActualAccount** demoted **NotReyz** from **Chief** to **Recruit**",
                demote.formattedMessage());

        assertNull(GuildMemberDetector.tryDetect(
                "ActualAccount has set NotReyz guild rank from Strategist to Strategist",
                guildComponent("ActualAccount has set NotReyz guild rank from Strategist to Strategist"),
                "Observer"));
    }

    @Test
    void detectsJoinLeaveAndKick() {
        GuildMemberDetector.Result join = GuildMemberDetector.tryDetect(
                "New_Member has joined the guild, say hello!",
                guildComponent("New_Member has joined the guild, say hello!"), "Observer");
        assertNotNull(join);
        assertEquals("**New\\_Member** has joined the guild!", join.formattedMessage());

        GuildMemberDetector.Result leave = GuildMemberDetector.tryDetect(
                "OldMember has left the guild.",
                guildComponent("OldMember has left the guild."), "Observer");
        assertNotNull(leave);
        assertEquals("**OldMember** has left the guild", leave.formattedMessage());

        Component kickComp = Component.empty()
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(GUILD_COLOR)))
                .append(TextFixtures.hoverText("catboy", "catboy's real username is FadeDave"))
                .append(" has kicked Slacker from the guild");
        GuildMemberDetector.Result kick = GuildMemberDetector.tryDetect(
                "catboy has kicked Slacker from the guild", kickComp, "Observer");
        assertNotNull(kick);
        assertEquals("**FadeDave** kicked **Slacker** from the guild", kick.formattedMessage());

        GuildMemberDetector.Result passiveKick = GuildMemberDetector.tryDetect(
                "Slacker has been kicked from the guild by FadeDave.",
                guildComponent("Slacker has been kicked from the guild by FadeDave."), "Observer");
        assertNotNull(passiveKick);
        assertEquals("**FadeDave** kicked **Slacker** from the guild", passiveKick.formattedMessage());
    }

    @Test
    void detectsInviteAndUninviteVariants() {
        GuildMemberDetector.Result inv1 = GuildMemberDetector.tryDetect(
                "Officer has invited Recruit to the guild",
                guildComponent("Officer has invited Recruit to the guild"), "Observer");
        assertNotNull(inv1);
        assertEquals("**Officer** invited **Recruit** to the guild", inv1.formattedMessage());

        Component inv2Comp = Component.empty()
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(GUILD_COLOR)))
                .append(TextFixtures.hoverText("Commander Lilacs", "Commander Lilacs's real username is RealLilacs"))
                .append(" invited NewMember to your guild!");
        GuildMemberDetector.Result inv2 = GuildMemberDetector.tryDetect(
                "Commander Lilacs invited NewMember to your guild!", inv2Comp, "Observer");
        assertNotNull(inv2);
        assertEquals("**RealLilacs** invited **NewMember** to the guild", inv2.formattedMessage());

        GuildMemberDetector.Result uninv = GuildMemberDetector.tryDetect(
                "[Guild] You have successfully uninvited NewMember from the guild",
                Component.literal("[Guild] You have successfully uninvited NewMember from the guild"),
                "GaztheCat");
        assertNotNull(uninv);
        assertEquals("**GaztheCat** uninvited **NewMember** from the guild", uninv.formattedMessage());
    }

    @Test
    void rejectsPlayerChatAndUnresolvedLocalActor() {
        assertNull(GuildMemberDetector.tryDetect(
                "FadeDave: did anyone see who left the guild",
                guildComponent("FadeDave: did anyone see who left the guild"), "Observer"));
        assertNull(GuildMemberDetector.tryDetect(
                "[Guild] You invited NewMember to the guild",
                Component.literal("[Guild] You invited NewMember to the guild"), null));
    }
}
