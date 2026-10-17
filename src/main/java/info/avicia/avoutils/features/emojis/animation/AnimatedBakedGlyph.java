package info.avicia.avoutils.features.emojis.animation;

import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.Style;

/**
 * A BakedGlyph wrapper for animated emoji frames that delegates visual rendering
 * to the active frame's glyph while locking glyph metrics (advance) to the base frame.
 * This eliminates horizontal text jitter and prevents following text from moving as frames advance.
 */
public record AnimatedBakedGlyph(BakedGlyph activeGlyph, GlyphInfo baseMetrics) implements BakedGlyph, EffectGlyph {

    @Override
    public GlyphInfo info() {
        return baseMetrics;
    }

    @Override
    public TextRenderable.Styled createGlyph(float x, float y, int color, int shadowColor, Style style, float boldOffset, float shadowOffset) {
        return activeGlyph.createGlyph(x, y, color, shadowColor, style, boldOffset, shadowOffset);
    }

    @Override
    public TextRenderable createEffect(float x, float y, float advance, float boldOffset, float shadowOffset, int color, int shadowColor, float alpha) {
        if (activeGlyph instanceof EffectGlyph effect) {
            return effect.createEffect(x, y, advance, boldOffset, shadowOffset, color, shadowColor, alpha);
        }
        return null;
    }
}

