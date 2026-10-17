package info.avicia.avoutils.mixin;

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
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Hides AvoUtils texture packs from the Minecraft Resource Packs menu,
 * while ensuring they remain active and preserved when the player adjusts other packs.
 */
@Mixin(PackSelectionModel.class)
public class ResourcePackOrganizerMixin {

    @Shadow @Final
    private PackRepository repository;

    @Shadow @Final
    List<Pack> selected;

    @Shadow @Final
    List<Pack> unselected;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void avoutils$removeAvoPacksOnInit(CallbackInfo ci) {
        this.selected.removeIf(profile -> profile.getId() != null && profile.getId().startsWith("avoutils/"));
        this.unselected.removeIf(profile -> profile.getId() != null && profile.getId().startsWith("avoutils/"));
    }

    @Inject(method = "findNewPacks", at = @At("RETURN"))
    private void avoutils$removeAvoPacksOnRefresh(CallbackInfo ci) {
        this.selected.removeIf(profile -> profile.getId() != null && profile.getId().startsWith("avoutils/"));
        this.unselected.removeIf(profile -> profile.getId() != null && profile.getId().startsWith("avoutils/"));
    }

    @Inject(method = "getSelected", at = @At("RETURN"), cancellable = true)
    private void avoutils$filterEnabledPacks(CallbackInfoReturnable<Stream<PackSelectionModel.Entry>> cir) {
        cir.setReturnValue(cir.getReturnValue().filter(pack -> pack.getId() == null || !pack.getId().startsWith("avoutils/")));
    }

    @Inject(method = "getUnselected", at = @At("RETURN"), cancellable = true)
    private void avoutils$filterDisabledPacks(CallbackInfoReturnable<Stream<PackSelectionModel.Entry>> cir) {
        cir.setReturnValue(cir.getReturnValue().filter(pack -> pack.getId() == null || !pack.getId().startsWith("avoutils/")));
    }

    @ModifyArg(
            method = "updateRepoSelectedList",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/repository/PackRepository;setSelected(Ljava/util/Collection;)V"
            )
    )
    private Collection<String> avoutils$preserveAvoPacksOnApply(Collection<String> enabledNames) {
        List<String> result = new ArrayList<>(enabledNames);
        if (this.repository != null) {
            for (String id : this.repository.getSelectedIds()) {
                if (id != null && id.startsWith("avoutils/") && !result.contains(id)) {
                    result.add(id);
                }
            }
        }
        return result;
    }
}

