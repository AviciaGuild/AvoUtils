package info.avicia.avoutils.mixin;

import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.features.emojis.EmojiTooltipComponent;
import info.avicia.avoutils.features.emojis.EmojiTooltipHelper;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Intercepts drawHoverEvent to render a large emoji preview when hovering over emoji text.
 */
@Mixin(GuiGraphics.class)
public abstract class DrawContextMixin {

    @Shadow
    public abstract void renderTooltip(
            Font textRenderer,
            List<ClientTooltipComponent> components,
            int x,
            int y,
            ClientTooltipPositioner positioner,
            @Nullable Identifier texture
    );

    @Inject(method = "renderComponentHoverEffect", at = @At("HEAD"), cancellable = true)
    private void avoutils$drawEmojiHoverTooltip(
            Font textRenderer,
            @Nullable Style style,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        if (style == null || style.getHoverEvent() == null) {
            return;
        }

        if (!WynncraftServerPolicy.isOnWynncraft()) {
            return;
        }

        EmojiTooltipHelper.EmojiHoverData data = EmojiTooltipHelper.parseEmojiHover(style.getHoverEvent());
        if (data != null) {
            List<ClientTooltipComponent> components = List.of(new EmojiTooltipComponent(data.replacement(), data.emojiName()));
            this.renderTooltip(
                    textRenderer,
                    components,
                    mouseX,
                    mouseY,
                    DefaultTooltipPositioner.INSTANCE,
                    null
            );
            ci.cancel();
        }
    }
}

