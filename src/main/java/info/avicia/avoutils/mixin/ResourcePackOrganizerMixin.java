package info.avicia.avoutils.mixin;

import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Hides AvoUtils texture packs from the Minecraft Resource Packs menu,
 * while ensuring they remain active and preserved when the player adjusts other packs.
 */
@Mixin(ResourcePackOrganizer.class)
public class ResourcePackOrganizerMixin {

    @Shadow @Final
    private ResourcePackManager resourcePackManager;

    @Shadow @Final
    List<ResourcePackProfile> enabledPacks;

    @Shadow @Final
    List<ResourcePackProfile> disabledPacks;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void avoutils$removeAvoPacksOnInit(CallbackInfo ci) {
        this.enabledPacks.removeIf(profile -> profile.getId() != null && profile.getId().startsWith("avoutils/"));
        this.disabledPacks.removeIf(profile -> profile.getId() != null && profile.getId().startsWith("avoutils/"));
    }

    @Inject(method = "refresh", at = @At("RETURN"))
    private void avoutils$removeAvoPacksOnRefresh(CallbackInfo ci) {
        this.enabledPacks.removeIf(profile -> profile.getId() != null && profile.getId().startsWith("avoutils/"));
        this.disabledPacks.removeIf(profile -> profile.getId() != null && profile.getId().startsWith("avoutils/"));
    }

    @Inject(method = "getEnabledPacks", at = @At("RETURN"), cancellable = true)
    private void avoutils$filterEnabledPacks(CallbackInfoReturnable<Stream<ResourcePackOrganizer.Pack>> cir) {
        cir.setReturnValue(cir.getReturnValue().filter(pack -> pack.getName() == null || !pack.getName().startsWith("avoutils/")));
    }

    @Inject(method = "getDisabledPacks", at = @At("RETURN"), cancellable = true)
    private void avoutils$filterDisabledPacks(CallbackInfoReturnable<Stream<ResourcePackOrganizer.Pack>> cir) {
        cir.setReturnValue(cir.getReturnValue().filter(pack -> pack.getName() == null || !pack.getName().startsWith("avoutils/")));
    }

    @ModifyArg(
            method = "refreshEnabledProfiles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resource/ResourcePackManager;setEnabledProfiles(Ljava/util/Collection;)V"
            )
    )
    private Collection<String> avoutils$preserveAvoPacksOnApply(Collection<String> enabledNames) {
        List<String> result = new ArrayList<>(enabledNames);
        if (this.resourcePackManager != null) {
            for (String id : this.resourcePackManager.getEnabledIds()) {
                if (id != null && id.startsWith("avoutils/") && !result.contains(id)) {
                    result.add(id);
                }
            }
        }
        return result;
    }
}

