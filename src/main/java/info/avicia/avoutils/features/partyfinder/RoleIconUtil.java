package info.avicia.avoutils.features.partyfinder;

/**
 * Party-finder role icon styling.
 */
public final class RoleIconUtil {

    private RoleIconUtil() {
    }

    public static String getStyledRolePrefix(String role) {
        if (role == null) {
            return "\u00a77\uD83E\uDDE9"; // §7 + puzzle piece
        }
        if (role.equalsIgnoreCase("dps")) {
            return "\u00a7c\uD83D\uDDE1"; // §c + dagger
        }
        if (role.equalsIgnoreCase("healer")) {
            return "\u00a7d\u2764"; // §d + heart
        }
        if (role.equalsIgnoreCase("tank")) {
            return "\u00a79\uD83D\uDEE1"; // §9 + shield
        }
        return "\u00a77\uD83E\uDDE9"; // §7 + puzzle piece
    }
}
