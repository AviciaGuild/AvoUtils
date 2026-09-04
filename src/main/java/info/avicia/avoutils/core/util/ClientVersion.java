package info.avicia.avoutils.core.util;

import info.avicia.avoutils.AvoUtilsMod;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Resolves the installed mod version used for backend compatibility checks.
 */
public final class ClientVersion {

    public static final String MOD_VERSION_HEADER = "X-AvoUtils-Version";

    private ClientVersion() {}

    public static String resolveInstalledVersion() {
        try {
            return FabricLoader.getInstance()
                    .getModContainer(AvoUtilsMod.MOD_ID)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        } catch (Throwable t) {
            return "unknown";
        }
    }
}

