package info.avicia.avoutils.features.partyfinder.api;

import info.avicia.avoutils.features.partyfinder.RoleIconUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartyDataTest {

    @Test
    void displayNameIncludesGuildTag() {
        PartyData.MemberData member = new PartyData.MemberData();
        member.name = "Steve";
        member.guildTag = "TAG";
        assertEquals("Steve §7[TAG]", member.displayName());
    }

    @Test
    void displayNameWithoutGuildTag() {
        PartyData.MemberData member = new PartyData.MemberData();
        member.name = "Steve";
        assertEquals("Steve", member.displayName());

        member.guildTag = "";
        assertEquals("Steve", member.displayName());
    }

    @Test
    void reservedMemberWithoutRoleShowsLock() {
        PartyData.MemberData member = new PartyData.MemberData();
        member.isReserved = true;
        assertEquals("§7\uD83D\uDD12", member.getStyledRolePrefix());
    }

    @Test
    void reservedMemberWithRoleShowsRoleIcon() {
        PartyData.MemberData member = new PartyData.MemberData();
        member.isReserved = true;
        member.role = "dps";
        assertEquals(RoleIconUtil.getStyledRolePrefix("dps"), member.getStyledRolePrefix());
    }

    @Test
    void normalMemberDelegatesToRoleIconUtil() {
        PartyData.MemberData member = new PartyData.MemberData();
        assertEquals(RoleIconUtil.getStyledRolePrefix(null), member.getStyledRolePrefix());
    }
}
