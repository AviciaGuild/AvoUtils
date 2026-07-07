package info.avicia.avoutils.mixin;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.features.emojis.EmojiFeature;
import info.avicia.avoutils.features.emojis.EmojiReplacer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Intercepts chat messages to replace :shortcode: patterns with PUA emoji characters.
 */
@Mixin(ChatHud.class)
public class ChatHudMixin {

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Text replaceEmojis(Text message) {
        if (message == null) {
            return null;
        }
        EmojiFeature feature = AvoUtilsMod.getInstance().getFeature(EmojiFeature.class);
        if (feature == null || !feature.isEnabled()) {
            return message;
        }
        try {
            return EmojiReplacer.replace(message);
        } catch (Exception e) {
            return message;
        }
    }
}
