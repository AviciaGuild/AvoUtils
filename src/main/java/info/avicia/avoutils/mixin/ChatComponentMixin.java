package info.avicia.avoutils.mixin;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.features.emojis.EmojiFeature;
import info.avicia.avoutils.features.emojis.EmojiReplacer;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Intercepts chat messages to replace :shortcode: patterns with PUA emoji characters.
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Component avoutils$replaceEmojis(Component message) {
        if (message == null) {
            return null;
        }
        if (!WynncraftServerPolicy.isOnWynncraft()) {
            return message;
        }
        AvoUtilsMod mod = AvoUtilsMod.getInstance();
        if (mod == null) {
            return message;
        }
        EmojiFeature feature = mod.getFeature(EmojiFeature.class);
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
