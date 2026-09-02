package info.avicia.avoutils.features.partyfinder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleIconUtilTest {

    @Test
    void nullRoleUsesPuzzlePiece() {
        assertEquals("\u00a77\uD83E\uDDE9", RoleIconUtil.getStyledRolePrefix(null));
    }

    @Test
    void mapsKnownRolesToIcons() {
        assertEquals("\u00a7c\uD83D\uDDE1", RoleIconUtil.getStyledRolePrefix("dps"));
        assertEquals("\u00a7d\u2764", RoleIconUtil.getStyledRolePrefix("healer"));
        assertEquals("\u00a79\uD83D\uDEE1", RoleIconUtil.getStyledRolePrefix("tank"));
        assertEquals("\u00a77\uD83E\uDDE9", RoleIconUtil.getStyledRolePrefix("other"));
    }

    @Test
    void roleMatchingIsCaseInsensitive() {
        assertEquals("\u00a7c\uD83D\uDDE1", RoleIconUtil.getStyledRolePrefix("DPS"));
        assertEquals("\u00a7d\u2764", RoleIconUtil.getStyledRolePrefix("Healer"));
    }

    @Test
    void unknownRoleUsesPuzzlePiece() {
        assertEquals("\u00a77\uD83E\uDDE9", RoleIconUtil.getStyledRolePrefix("support"));
    }
}
