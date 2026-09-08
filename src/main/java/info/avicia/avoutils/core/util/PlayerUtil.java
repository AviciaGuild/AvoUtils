package info.avicia.avoutils.core.util;

import net.minecraft.client.MinecraftClient;

import java.util.Locale;

/**
 * Shared helpers for the local player's identity and case-insensitive name matching.
 */
public final class PlayerUtil {

    private PlayerUtil() {
    }

    /**
     * The local player's account username, or null if not available (e.g. before login).
     */
    public static String selfName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null && mc.player.getName() != null) {
            String name = mc.player.getName().getString();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return (mc != null && mc.getSession() != null) ? mc.getSession().getUsername() : null;
    }

    /**
     * Checks whether the given player name matches the local player's identity,
     * checking both the active player entity name and the logged-in session name.
     */
    public static boolean isSelf(String name) {
        if (name == null) {
            return false;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null && namesEqual(name, mc.player.getName().getString())) {
            return true;
        }
        return namesEqual(name, selfName());
    }

    /**
     * Null-safe case-insensitive equality for two player names.
     */
    public static boolean namesEqual(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    /**
     * Normalize a name to lower-case for map/set keys. Returns an empty string for null.
     */
    public static String normalizeName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    /**
     * Checks if {@code src} contains {@code what} case-insensitively without string allocations.
     */
    public static boolean containsIgnoreCase(String src, String what) {
        if (src == null || what == null) return false;
        final int length = what.length();
        if (length == 0) return true;
        for (int i = src.length() - length; i >= 0; i--) {
            if (src.regionMatches(true, i, what, 0, length)) return true;
        }
        return false;
    }
}
