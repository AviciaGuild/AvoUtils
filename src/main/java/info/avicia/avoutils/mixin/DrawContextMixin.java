package info.avicia.avoutils.mixin;

import info.avicia.avoutils.features.emojis.EmojiTooltipComponent;
import info.avicia.avoutils.features.emojis.EmojiTooltipHelper;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Intercepts drawHoverEvent to render a large emoji preview when hovering over emoji text.
 */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {

    @Shadow
    public abstract void drawTooltipImmediately(
            TextRenderer textRenderer,
            List<TooltipComponent> components,
            int x,
            int y,
            TooltipPositioner positioner,
            @Nullable Identifier texture
    );

    @Inject(method = "drawHoverEvent", at = @At("HEAD"), cancellable = true)
    private void avoutils$drawEmojiHoverTooltip(
            TextRenderer textRenderer,
            @Nullable Style style,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        if (style == null || style.getHoverEvent() == null) {
            return;
        }

        EmojiTooltipHelper.EmojiHoverData data = EmojiTooltipHelper.parseEmojiHover(style.getHoverEvent());
        if (data != null) {
            List<TooltipComponent> components = List.of(new EmojiTooltipComponent(data.replacement(), data.emojiName()));
            this.drawTooltipImmediately(
                    textRenderer,
                    components,
                    mouseX,
                    mouseY,
                    HoveredTooltipPositioner.INSTANCE,
                    null
            );
            ci.cancel();
        }
    }
}

