package info.avicia.avoutils.mixin;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.features.guildstorage.GuildStorageNotifier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into {@link HandledScreen#render} to feed the current container's
 * slots to {@link GuildStorageNotifier} so it can scan for guild storage lore.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow
    protected abstract ScreenHandler getScreenHandler();

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            GuildStorageNotifier notifier = AvoUtilsMod.getInstance().getFeature(GuildStorageNotifier.class);
            if (notifier != null) {
                notifier.onContainerRender(getScreenHandler());
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[GuildStorageNotifier] Error in render hook", e);
        }
    }
}
