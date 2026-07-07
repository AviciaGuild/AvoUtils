package info.avicia.avoutils.mixin;

import info.avicia.avoutils.features.emojis.EmojiResourcePackProvider;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * Adds EmojiResourcePackProvider to the resource pack manager's provider set
 */
@Mixin(ResourcePackManager.class)
public class ResourcePackManagerMixin {

    @Shadow @Final @Mutable
    private Set<ResourcePackProvider> providers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        Set<ResourcePackProvider> newProviders = new HashSet<>(providers);
        newProviders.add(new EmojiResourcePackProvider());
        providers = newProviders;
    }
}
