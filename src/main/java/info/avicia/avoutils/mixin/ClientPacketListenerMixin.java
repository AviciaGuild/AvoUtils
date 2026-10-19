package info.avicia.avoutils.mixin;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.party.InGamePartyTracker;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.features.chatbridge.ChatBridgeFeature;
import info.avicia.avoutils.features.wardetector.WarDetectorFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void avoutils$onGameMessage(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || !mc.isSameThread()) {
                return;
            }
            if (packet.overlay()) {
                return;
            }
            if (!WynncraftServerPolicy.isOnWynncraft()) {
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
                WarDetectorFeature war = AvoUtilsMod.getInstance().getFeature(WarDetectorFeature.class);
                if (war != null) {
                    war.onSystemChat(packet.content());
                }
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] Error handling game message in mixin", e);
        }
    }
}
