package info.avicia.avoutils.mixin;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.features.guildstorage.GuildStorageFeature;
import info.avicia.avoutils.features.rewards.RewardsFeature;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into {@link AbstractContainerScreen}: {@code render} feeds the current container's slots to
 * {@link GuildStorageFeature} so it can scan for guild storage lore, and draws {@link RewardsFeature}'s panel, which
 * also gets the clicks and the scroll wheel, and during a hand-out the releases and the keys.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    protected int imageHeight;

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
                GuildStorageFeature notifier = mod.getFeature(GuildStorageFeature.class);
                if (notifier != null) {
                    notifier.onContainerRender(getMenu());
                }
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] [GuildStorage] Error in render hook", e);
        }
        try {
            RewardsFeature rewards = avoutils$rewards();
            if (rewards != null) {
                rewards.renderPanel(avoutils$self(), context, mouseX, mouseY, delta, leftPos, topPos, imageHeight);
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] [Rewards] Error in render hook", e);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void avoutils$onMouseClicked(MouseButtonEvent click, boolean doubleClick,
                                         CallbackInfoReturnable<Boolean> cir) {
        try {
            RewardsFeature rewards = avoutils$rewards();
            if (rewards != null && rewards.onMouseClicked(avoutils$self(), click, doubleClick)) {
                cir.setReturnValue(true);
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] [Rewards] Error in click hook", e);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void avoutils$onMouseReleased(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        try {
            RewardsFeature rewards = avoutils$rewards();
            if (rewards != null && rewards.onMouseReleased(avoutils$self())) {
                cir.setReturnValue(true);
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] [Rewards] Error in release hook", e);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void avoutils$onMouseScrolled(double mouseX, double mouseY, double horizontalAmount,
                                          double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        try {
            RewardsFeature rewards = avoutils$rewards();
            if (rewards != null && rewards.onMouseScrolled(avoutils$self(), mouseX, mouseY, verticalAmount)) {
                cir.setReturnValue(true);
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] [Rewards] Error in scroll hook", e);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void avoutils$onKeyPressed(KeyEvent key, CallbackInfoReturnable<Boolean> cir) {
        try {
            RewardsFeature rewards = avoutils$rewards();
            if (rewards != null && rewards.onKeyPressed(avoutils$self(), key)) {
                cir.setReturnValue(true);
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] [Rewards] Error in key hook", e);
        }
    }

    private AbstractContainerScreen<?> avoutils$self() {
        return (AbstractContainerScreen<?>) (Object) this;
    }

    private static RewardsFeature avoutils$rewards() {
        AvoUtilsMod mod = AvoUtilsMod.getInstance();
        return mod != null && WynncraftServerPolicy.isOnWynncraft() ? mod.getFeature(RewardsFeature.class) : null;
    }
}
