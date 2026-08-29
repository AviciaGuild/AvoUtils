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
        return mc.getSession() != null ? mc.getSession().getUsername() : null;
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
}
