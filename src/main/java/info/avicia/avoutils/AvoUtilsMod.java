package info.avicia.avoutils;

import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.features.partyfinder.PartyFinderFeature;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class AvoUtilsMod implements ClientModInitializer {
    public static final String MOD_ID = "avoutils";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static AvoUtilsMod instance;

    private ModConfig config;
    private final List<AvoFeature> features = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        instance = this;

        // Load configs
        config = ModConfig.load();

        // Register mod features
        registerFeature(new PartyFinderFeature());

        // Initialize all registered features
        for (AvoFeature feature : features) {
            feature.initialize(config);
        }

        LOGGER.info("AvoUtils mod initialized.");
    }

    private void registerFeature(AvoFeature feature) {
        features.add(feature);
    }

    public static AvoUtilsMod getInstance() {
        return instance;
    }

    public ModConfig getConfig() {
        return config;
    }

    public <T extends AvoFeature> T getFeature(Class<T> featureClass) {
        for (AvoFeature feature : features) {
            if (featureClass.isInstance(feature)) {
                return featureClass.cast(feature);
            }
        }
        return null;
    }
}
