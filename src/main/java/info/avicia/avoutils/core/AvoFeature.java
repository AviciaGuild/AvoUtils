package info.avicia.avoutils.core;

import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.util.WynncraftServerPolicy.Scope;

/**
 * Base interface for all modular features in AvoUtils
 */
public interface AvoFeature {
    void initialize(ModConfig config);

    default void onServerScopeChanged(Scope newScope) {}
}
