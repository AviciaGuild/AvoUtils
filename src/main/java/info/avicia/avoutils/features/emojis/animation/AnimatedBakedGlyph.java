package info.avicia.avoutils.features.emojis.animation;

import net.minecraft.client.font.BakedGlyph;
import net.minecraft.client.font.EffectGlyph;
import net.minecraft.client.font.GlyphMetrics;
import net.minecraft.client.font.TextDrawable;
import net.minecraft.text.Style;

/**
 * A BakedGlyph wrapper for animated emoji frames that delegates visual rendering
 * to the active frame's glyph while locking glyph metrics (advance) to the base frame.
 * This eliminates horizontal text jitter and prevents following text from moving as frames advance.
 */
public record AnimatedBakedGlyph(BakedGlyph activeGlyph, GlyphMetrics baseMetrics) implements BakedGlyph, EffectGlyph {

    @Override
    public GlyphMetrics getMetrics() {
        return baseMetrics;
    }

    @Override
    public TextDrawable.DrawnGlyphRect create(float x, float y, int color, int shadowColor, Style style, float boldOffset, float shadowOffset) {
        return activeGlyph.create(x, y, color, shadowColor, style, boldOffset, shadowOffset);
    }

    @Override
    public TextDrawable create(float x, float y, float advance, float boldOffset, float shadowOffset, int color, int shadowColor, float alpha) {
        if (activeGlyph instanceof EffectGlyph effect) {
            return effect.create(x, y, advance, boldOffset, shadowOffset, color, shadowColor, alpha);
        }
        return null;
    }
}

