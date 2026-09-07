package info.avicia.avoutils.features.emojis.animation;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnimatedImageDecoderTest {

    @Test
    void decodeStaticPngReturnsSingleFrame() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);

        List<AnimationFrame> frames = AnimatedImageDecoder.decode(baos.toByteArray());
        assertEquals(1, frames.size());
        assertEquals(32, frames.get(0).image().getWidth()); // Scaled up to target size 32
        assertEquals(32, frames.get(0).image().getHeight());
    }

    @Test
    void scaleToTargetPreservesAspectAndCenters() {
        BufferedImage wide = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        BufferedImage scaled = AnimatedImageDecoder.scaleToTarget(wide, 32);

        assertEquals(32, scaled.getWidth());
        assertEquals(32, scaled.getHeight());
    }

    @Test
    void stitchVerticallyCreatesProperStrip() {
        List<AnimationFrame> frames = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            BufferedImage f = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            frames.add(new AnimationFrame(f, 100));
        }

        BufferedImage strip = AnimatedImageDecoder.stitchVertically(frames);
        assertEquals(32, strip.getWidth());
        assertEquals(32 * 4, strip.getHeight()); // 128px high
    }

    @Test
    void decodeEmptyReturnsEmptyList() throws Exception {
        assertTrue(AnimatedImageDecoder.decode(null).isEmpty());
        assertTrue(AnimatedImageDecoder.decode(new byte[0]).isEmpty());
    }

    @Test
    void scaleToTargetHandlesNullAndExtremeRatios() {
        assertNull(AnimatedImageDecoder.scaleToTarget(null, 32));

        BufferedImage sliver = new BufferedImage(1000, 1, BufferedImage.TYPE_INT_ARGB);
        BufferedImage scaled = AnimatedImageDecoder.scaleToTarget(sliver, 32);
        assertNotNull(scaled);
        assertEquals(32, scaled.getWidth());
        assertEquals(32, scaled.getHeight());
    }

    @Test
    void stitchVerticallyHandlesNullAndEmpty() {
        BufferedImage empty = AnimatedImageDecoder.stitchVertically(List.of());
        assertEquals(32, empty.getWidth());
        assertEquals(32, empty.getHeight());

        BufferedImage fromNull = AnimatedImageDecoder.stitchVertically(null);
        assertEquals(32, fromNull.getWidth());
        assertEquals(32, fromNull.getHeight());
    }

    @Test
    void normalizeFrameAdvancesEqualizesDisparateFrameBounds() {
        BufferedImage frame0 = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        frame0.setRGB(18, 10, 0xFFFF0000); // Frame 0 extends to x = 18

        BufferedImage frame1 = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        frame1.setRGB(27, 15, 0xFF00FF00); // Frame 1 extends to x = 27 (widest)

        BufferedImage frame2 = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        frame2.setRGB(12, 5, 0xFF0000FF);  // Frame 2 extends to x = 12

        List<AnimationFrame> frames = List.of(
                new AnimationFrame(frame0, 100),
                new AnimationFrame(frame1, 100),
                new AnimationFrame(frame2, 100)
        );

        BufferedImage stitched = AnimatedImageDecoder.stitchVertically(frames);

        // Verify that for all 3 frames, the rightmost column with alpha > 0 is exactly column 27
        for (int f = 0; f < 3; f++) {
            int yOffset = f * 32;
            int rightmostWithAlpha = -1;
            for (int x = 31; x >= 0; x--) {
                for (int y = 0; y < 32; y++) {
                    int alpha = (stitched.getRGB(x, yOffset + y) >> 24) & 0xFF;
                    if (alpha > 0) {
                        rightmostWithAlpha = x;
                        break;
                    }
                }
                if (rightmostWithAlpha != -1) break;
            }
            assertEquals(27, rightmostWithAlpha, "Frame " + f + " must have rightmost column 27");
        }

        // Verify the anchor pixel has alpha = 1 (imperceptible)
        assertEquals(1, (stitched.getRGB(27, 0) >> 24) & 0xFF, "Frame 0 anchor pixel alpha");
        assertEquals(255, (stitched.getRGB(27, 32 + 15) >> 24) & 0xFF, "Frame 1 natural pixel alpha");
        assertEquals(1, (stitched.getRGB(27, 64) >> 24) & 0xFF, "Frame 2 anchor pixel alpha");
    }

    @Test
    void normalizeFrameAdvancesEdgeCases() {
        assertFalse(AnimatedImageDecoder.normalizeFrameAdvances(null, 2));
        assertFalse(AnimatedImageDecoder.normalizeFrameAdvances(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB), 1));

        // Completely transparent sheet
        BufferedImage transparentSheet = new BufferedImage(32, 64, BufferedImage.TYPE_INT_ARGB);
        assertFalse(AnimatedImageDecoder.normalizeFrameAdvances(transparentSheet, 2));

        // Already normalized sheet
        BufferedImage alreadyNormalized = new BufferedImage(32, 64, BufferedImage.TYPE_INT_ARGB);
        alreadyNormalized.setRGB(25, 5, 0xFFFF0000);
        alreadyNormalized.setRGB(25, 32 + 5, 0xFF00FF00);
        assertFalse(AnimatedImageDecoder.normalizeFrameAdvances(alreadyNormalized, 2));
    }
}
