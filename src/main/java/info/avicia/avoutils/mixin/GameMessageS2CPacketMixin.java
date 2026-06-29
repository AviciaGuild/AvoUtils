package info.avicia.avoutils.mixin;

import info.avicia.avoutils.features.emojis.EmojiReplacer;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameMessageS2CPacket.class)
public class GameMessageS2CPacketMixin {

    @Unique
    private Text avoUtils$replacedContent;

    @Inject(method = "content", at = @At("RETURN"), cancellable = true)
    private void onGetContent(CallbackInfoReturnable<Text> cir) {
        Text original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        if (avoUtils$replacedContent == null) {
            try {
                avoUtils$replacedContent = EmojiReplacer.replace(original);
            } catch (Exception e) {
                avoUtils$replacedContent = original;
            }
        }
        if (avoUtils$replacedContent != original) {
            cir.setReturnValue(avoUtils$replacedContent);
        }
    }
}
