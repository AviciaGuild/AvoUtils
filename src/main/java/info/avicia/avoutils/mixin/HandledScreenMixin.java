package info.avicia.avoutils.mixin;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.features.guildstorage.GuildStorageNotifier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into {@link AbstractContainerScreen#render} to feed the current container's
 * slots to {@link GuildStorageNotifier} so it can scan for guild storage lore.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin {

    @Shadow
    protected abstract AbstractContainerMenu getMenu();

    @Inject(method = "render", at = @At("TAIL"))
    private void avoutils$onRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            if (!WynncraftServerPolicy.isOnWynncraft()) {
                return;
            }
            AvoUtilsMod mod = AvoUtilsMod.getInstance();
            if (mod != null) {
                GuildStorageNotifier notifier = mod.getFeature(GuildStorageNotifier.class);
                if (notifier != null) {
                    notifier.onContainerRender(getMenu());
                }
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] [GuildStorage] Error in render hook", e);
        }
    }
}
