package info.avicia.avoutils.core;
 
import info.avicia.avoutils.core.config.ModConfig;
 
/**
 * Base interface for all modular features in AvoUtils
 */
public interface AvoFeature {
    void initialize(ModConfig config);
}
