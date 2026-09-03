package info.avicia.avoutils.mixin;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.party.InGamePartyTracker;
import info.avicia.avoutils.features.chatbridge.ChatBridgeFeature;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onGameMessage", at = @At("HEAD"), cancellable = true)
    private void avoutils$onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        try {
            if (!net.minecraft.client.MinecraftClient.getInstance().isOnThread()) {
                return;
            }
            if (packet.overlay()) {
                return;
            }
            if (packet.content() != null && AvoUtilsMod.getInstance() != null) {
                String text = packet.content().getString();
                if (text != null) {
                    if (InGamePartyTracker.getInstance().onChatMessage(text)) {
                        ci.cancel();
                    }
                }
                ChatBridgeFeature cb = AvoUtilsMod.getInstance().getFeature(ChatBridgeFeature.class);
                if (cb != null) {
                    cb.onSystemChat(packet.content());
                }
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Error handling game message in mixin", e);
        }
    }
}
