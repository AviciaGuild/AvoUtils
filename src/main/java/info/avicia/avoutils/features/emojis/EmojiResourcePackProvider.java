package info.avicia.avoutils.features.emojis;

import info.avicia.avoutils.AvoUtilsMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.*;
import net.minecraft.text.Text;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A ResourcePackProvider that serves emoji packs from avoutils/emojis/
 */
public class EmojiResourcePackProvider implements ResourcePackProvider {

    private static final Path EMOJI_DIR = FabricLoader.getInstance().getGameDir().resolve("avoutils/emojis");

    @Override
    public void register(Consumer<ResourcePackProfile> profileAdder) {
        EmojiFeature feature = AvoUtilsMod.getInstance().getFeature(EmojiFeature.class);
        if (feature == null || !feature.isEnabled()) {
            return;
        }

        registerIfExists(profileAdder,
                EMOJI_DIR.resolve("avoutils-twemoji.zip"),
                "avoutils/twemoji",
                "AvoUtils Twemoji",
                new ZipResourcePack.ZipBackedFactory(EMOJI_DIR.resolve("avoutils-twemoji.zip")));

        registerIfExists(profileAdder,
                EMOJI_DIR.resolve("avoutils-emojis"),
                "avoutils/emojis",
                "AvoUtils Emojis",
                new DirectoryResourcePack.DirectoryBackedFactory(EMOJI_DIR.resolve("avoutils-emojis")));
    }

    private static void registerIfExists(Consumer<ResourcePackProfile> profileAdder,
                                         Path path, String id, String name,
                                         ResourcePackProfile.PackFactory factory) {
        if (!Files.exists(path)) return;

        try {
            ResourcePackInfo info = new ResourcePackInfo(
                    id,
                    Text.literal(name),
                    ResourcePackSource.BUILTIN,
                    Optional.empty());
            ResourcePackPosition position = new ResourcePackPosition(true,
                    ResourcePackProfile.InsertionPosition.TOP, false);
            ResourcePackProfile profile = ResourcePackProfile.create(info,
                    factory, ResourceType.CLIENT_RESOURCES, position);
            profileAdder.accept(profile);
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Failed to create resource pack '{}'", name, e);
        }
    }
}
