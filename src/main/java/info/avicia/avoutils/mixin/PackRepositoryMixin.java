package info.avicia.avoutils.mixin;

import info.avicia.avoutils.features.emojis.EmojiResourcePackProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;

/**
 * Adds EmojiResourcePackProvider to the resource pack manager's provider set
 */
@Mixin(PackRepository.class)
public class PackRepositoryMixin {

    @Shadow @Final @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void avoutils$onInit(CallbackInfo ci) {
        Set<RepositorySource> newProviders = new HashSet<>(sources);
        newProviders.add(new EmojiResourcePackProvider());
        sources = newProviders;
    }
}
