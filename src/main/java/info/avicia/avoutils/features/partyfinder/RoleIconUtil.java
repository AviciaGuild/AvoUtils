package info.avicia.avoutils.features.partyfinder;

import java.util.Locale;

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
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "dps" -> "\u00a7c\uD83D\uDDE1"; // §c + dagger
            case "healer" -> "\u00a7d\u2764"; // §d + heart
            case "tank" -> "\u00a79\uD83D\uDEE1"; // §9 + shield
            case "other" -> "\u00a77\uD83E\uDDE9"; // §7 + puzzle piece
            default -> "\u00a77\uD83E\uDDE9"; // §7 + puzzle piece
        };
    }
}
