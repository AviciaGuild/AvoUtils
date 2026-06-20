package info.avicia.partyfinder.mixin;

import info.avicia.partyfinder.PartyFinderMod;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onGameMessage", at = @At("HEAD"), cancellable = true)
    private void onOnGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        try {
            if (!net.minecraft.client.MinecraftClient.getInstance().isOnThread()) {
                return;
            }
            if (packet.content() != null) {
                String text = packet.content().getString();
                if (text != null && PartyFinderMod.getInstance() != null && PartyFinderMod.getInstance().getChatDetector() != null) {
                    if (PartyFinderMod.getInstance().getChatDetector().onChatMessage(text)) {
                        ci.cancel();
                    }
                }
            }
        } catch (Exception e) {
            PartyFinderMod.LOGGER.error("Error handling game message in mixin", e);
        }
    }
}
