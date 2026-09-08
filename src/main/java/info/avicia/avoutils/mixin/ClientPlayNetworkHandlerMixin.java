package info.avicia.avoutils.mixin;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.party.InGamePartyTracker;
import info.avicia.avoutils.features.chatbridge.ChatBridgeFeature;
import net.minecraft.client.MinecraftClient;
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
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || !mc.isOnThread()) {
                return;
            }
            if (packet.overlay()) {
                return;
            }
            if (packet.content() != null && AvoUtilsMod.getInstance() != null) {
                if (InGamePartyTracker.getInstance().onChatMessage(packet.content())) {
                    ci.cancel();
                }
                ChatBridgeFeature cb = AvoUtilsMod.getInstance().getFeature(ChatBridgeFeature.class);
                if (cb != null) {
                    cb.onSystemChat(packet.content());
                }
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] Error handling game message in mixin", e);
        }
    }
}
