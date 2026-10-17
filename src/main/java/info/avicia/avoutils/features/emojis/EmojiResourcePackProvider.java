package info.avicia.avoutils.features.emojis;

import info.avicia.avoutils.AvoUtilsMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A ResourcePackProvider that serves emoji packs from avoutils/emojis/
 */
public class EmojiResourcePackProvider implements RepositorySource {

    private static final Path EMOJI_DIR = FabricLoader.getInstance().getGameDir().resolve("avoutils/emojis");

    @Override
    public void loadPacks(Consumer<Pack> profileAdder) {
        AvoUtilsMod mod = AvoUtilsMod.getInstance();
        if (mod == null) {
            return;
        }

        EmojiFeature feature = mod.getFeature(EmojiFeature.class);
        if (feature == null || !feature.isEnabled()) {
            return;
        }

        registerIfExists(profileAdder,
                EMOJI_DIR.resolve("avoutils-twemoji.zip"),
                "avoutils/twemoji",
                "AvoUtils Twemoji",
                new FilePackResources.FileResourcesSupplier(EMOJI_DIR.resolve("avoutils-twemoji.zip")));

        registerIfExists(profileAdder,
                EMOJI_DIR.resolve("avoutils-emojis"),
                "avoutils/emojis",
                "AvoUtils Emojis",
                new PathPackResources.PathResourcesSupplier(EMOJI_DIR.resolve("avoutils-emojis")));
    }

    private static void registerIfExists(Consumer<Pack> profileAdder,
                                         Path path, String id, String name,
                                         Pack.ResourcesSupplier factory) {
        if (!Files.exists(path)) return;

        try {
            PackLocationInfo info = new PackLocationInfo(
                    id,
                    Component.literal(name),
                    PackSource.BUILT_IN,
                    Optional.empty());
            PackSelectionConfig position = new PackSelectionConfig(true,
                    Pack.Position.TOP, false);
            Pack profile = Pack.readMetaAndCreate(info,
                    factory, PackType.CLIENT_RESOURCES, position);
            profileAdder.accept(profile);
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] Failed to create resource pack '{}'", name, e);
        }
    }
}
