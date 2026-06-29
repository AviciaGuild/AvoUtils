package info.avicia.avoutils.mixin;

import info.avicia.avoutils.features.emojis.EmojiReplacer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Text modifySystemMessage(Text message) {
        if (message == null) {
            return message;
        }
        try {
            return EmojiReplacer.replace(message);
        } catch (Exception e) {
            return message;
        }
    }

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Text modifyChatMessage(Text message) {
        if (message == null) {
            return message;
        }
        try {
            return EmojiReplacer.replace(message);
        } catch (Exception e) {
            return message;
        }
    }
}
