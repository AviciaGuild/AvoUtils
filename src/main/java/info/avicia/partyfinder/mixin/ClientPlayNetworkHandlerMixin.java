package info.avicia.partyfinder.mixin;

import info.avicia.partyfinder.PartyFinderMod;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void onOnGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        try {
            if (packet.content() != null) {
                String text = packet.content().getString();
                if (text != null && PartyFinderMod.getInstance() != null && PartyFinderMod.getInstance().getChatDetector() != null) {
                    PartyFinderMod.getInstance().getChatDetector().onChatMessage(text);
                }
            }
        } catch (Exception e) {
            PartyFinderMod.LOGGER.error("Error handling game message in mixin", e);
        }
    }

    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void onOnChatMessage(ChatMessageS2CPacket packet, CallbackInfo ci) {
        try {
            String text = null;
            if (packet.unsignedContent() != null) {
                text = packet.unsignedContent().getString();
            } else if (packet.body() != null) {
                text = packet.body().content();
            }
            if (text != null && PartyFinderMod.getInstance() != null && PartyFinderMod.getInstance().getChatDetector() != null) {
                PartyFinderMod.getInstance().getChatDetector().onChatMessage(text);
            }
        } catch (Exception e) {
            PartyFinderMod.LOGGER.error("Error handling chat message in mixin", e);
        }
    }
}
