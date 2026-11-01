package info.avicia.avoutils.mixin;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.features.emojis.EmojiFeature;
import info.avicia.avoutils.features.emojis.animation.AnimatedBakedGlyph;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts font glyph retrieval to dynamically redirect animated emoji code points
 * to the active frame's glyph while locking glyph metrics (advance) to the base frame.
 * This eliminates horizontal text jitter and prevents following text from moving.
 */
@Mixin(Font.class)
public abstract class FontMixin {

    @Shadow
    abstract GlyphSource getGlyphSource(FontDescription font);

    @Inject(method = "getGlyph", at = @At("HEAD"), cancellable = true)
    private void avoutils$redirectAnimatedGlyph(int codePoint, Style style, CallbackInfoReturnable<BakedGlyph> cir) {
        // Animated emojis are allocated strictly in Plane 15 (>= 0xF0000).
        // Bypasses all overhead for all standard text, numbers, symbols, and UI fonts.
        if (codePoint < 0xF0000) return;
        if (!WynncraftServerPolicy.isOnWynncraft()) return;

        AvoUtilsMod mod = AvoUtilsMod.getInstance();
        if (mod == null) return;

        EmojiFeature feature = mod.getFeature(EmojiFeature.class);
        if (feature == null || !feature.isEnabled()) return;

        int activeCodePoint = feature.getAnimatedFrameCodePoint(codePoint);
        if (activeCodePoint == codePoint) return;

        GlyphSource provider = this.getGlyphSource(style.getFont());
        if (provider == null) return;

        BakedGlyph baseGlyph = provider.getGlyph(codePoint);
        BakedGlyph activeGlyph = provider.getGlyph(activeCodePoint);

        if (activeGlyph != null && baseGlyph != null) {
            cir.setReturnValue(new AnimatedBakedGlyph(activeGlyph, baseGlyph.info()));
        }
    }
}

