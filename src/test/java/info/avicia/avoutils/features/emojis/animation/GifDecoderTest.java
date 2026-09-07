package info.avicia.avoutils.features.emojis.animation;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GifDecoderTest {

    @Test
    void isGifDetectsGifHeaders() {
        byte[] gif89 = "GIF89a...".getBytes();
        byte[] gif87 = "GIF87a...".getBytes();
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] shortData = new byte[]{1, 2, 3};

        assertTrue(GifDecoder.isGif(gif89));
        assertTrue(GifDecoder.isGif(gif87));
        assertFalse(GifDecoder.isGif(png));
        assertFalse(GifDecoder.isGif(shortData));
        assertFalse(GifDecoder.isGif(null));
    }

    @Test
    void decodeHandlesNullAndEmpty() throws Exception {
        assertTrue(GifDecoder.decode(null).isEmpty());
        assertTrue(GifDecoder.decode(new byte[0]).isEmpty());
    }

    @Test
    void decodeReadsStaticGifAsSingleFrame() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 10, 10);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "gif", baos);
        byte[] gifBytes = baos.toByteArray();

        List<AnimationFrame> frames = GifDecoder.decode(gifBytes);
        assertNotNull(frames);
        assertEquals(1, frames.size());
        assertEquals(10, frames.get(0).image().getWidth());
        assertEquals(10, frames.get(0).image().getHeight());
    }

    @Test
    void decodeReadsMultiFrameGif() throws Exception {
        // Minimal valid 2-frame 1x1 animated GIF89a
        byte[] animatedGif = new byte[]{
                'G', 'I', 'F', '8', '9', 'a',
                0x01, 0x00, 0x01, 0x00, (byte) 0x80, 0x00, 0x00, // Screen descriptor (1x1, GCT 2 colors)
                (byte) 0xFF, 0x00, 0x00, 0x00, (byte) 0xFF, 0x00, // GCT: Red, Green
                // Frame 1: delay = 10 (100ms)
                0x21, (byte) 0xF9, 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00,
                0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                0x02, 0x02, 0x44, 0x01, 0x00,
                // Frame 2: delay = 20 (200ms)
                0x21, (byte) 0xF9, 0x04, 0x00, 0x14, 0x00, 0x00, 0x00,
                0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                0x02, 0x02, 0x4C, 0x01, 0x00,
                0x3B // Trailer
        };

        List<AnimationFrame> frames = GifDecoder.decode(animatedGif);
        assertNotNull(frames);
        assertEquals(2, frames.size());
        assertEquals(100, frames.get(0).delayMs());
        assertEquals(200, frames.get(1).delayMs());
    }

    @Test
    void decodeUsesLogicalScreenDimensionsWhenLargerThanFrame0() throws Exception {
        // 2-frame GIF where logical screen is 4x4, but frame 1 is 1x1 at (0, 0)
        byte[] animatedGif = new byte[]{
                'G', 'I', 'F', '8', '9', 'a',
                0x04, 0x00, 0x04, 0x00, (byte) 0x80, 0x00, 0x00, // Logical Screen: 4x4
                (byte) 0xFF, 0x00, 0x00, 0x00, (byte) 0xFF, 0x00,
                // Frame 1: 1x1 at (0, 0)
                0x21, (byte) 0xF9, 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00,
                0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                0x02, 0x02, 0x44, 0x01, 0x00,
                // Frame 2: 1x1 at (2, 2)
                0x21, (byte) 0xF9, 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00,
                0x2C, 0x02, 0x00, 0x02, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                0x02, 0x02, 0x44, 0x01, 0x00,
                0x3B
        };

        List<AnimationFrame> frames = GifDecoder.decode(animatedGif);
        assertNotNull(frames);
        assertEquals(2, frames.size());
        assertEquals(4, frames.get(0).image().getWidth());
        assertEquals(4, frames.get(0).image().getHeight());
        assertEquals(4, frames.get(1).image().getWidth());
        assertEquals(4, frames.get(1).image().getHeight());
    }

    @Test
    void decodeExpandsCanvasWhenFrameDescriptorExceedsBounds() throws Exception {
        // Logical screen is 2x2, but Frame 2 is at offset (3, 0) with width 1 (total width 4)
        byte[] animatedGif = new byte[]{
                'G', 'I', 'F', '8', '9', 'a',
                0x02, 0x00, 0x02, 0x00, (byte) 0x80, 0x00, 0x00, // Logical Screen: 2x2
                (byte) 0xFF, 0x00, 0x00, 0x00, (byte) 0xFF, 0x00,
                // Frame 1: 1x1 at (0, 0)
                0x21, (byte) 0xF9, 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00,
                0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                0x02, 0x02, 0x44, 0x01, 0x00,
                // Frame 2: 1x1 at (3, 0) -> requires width 4
                0x21, (byte) 0xF9, 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00,
                0x2C, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                0x02, 0x02, 0x44, 0x01, 0x00,
                0x3B
        };

        List<AnimationFrame> frames = GifDecoder.decode(animatedGif);
        assertNotNull(frames);
        assertEquals(2, frames.size());
        assertTrue(frames.get(1).image().getWidth() >= 4);
    }

    @Test
    void decodeHandlesZeroLogicalScreenDimensions() throws Exception {
        // GIF header with logical screen width=0, height=0, but frame 1x1
        byte[] gifWithZeroScreen = new byte[]{
                'G', 'I', 'F', '8', '9', 'a',
                0x00, 0x00, 0x00, 0x00, (byte) 0x80, 0x00, 0x00, // Logical Screen: 0x0
                (byte) 0xFF, 0x00, 0x00, 0x00, (byte) 0xFF, 0x00,
                // Frame 1: 1x1 at (0, 0)
                0x21, (byte) 0xF9, 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00,
                0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                0x02, 0x02, 0x44, 0x01, 0x00,
                0x3B
        };

        List<AnimationFrame> frames = GifDecoder.decode(gifWithZeroScreen);
        assertNotNull(frames);
        assertFalse(frames.isEmpty());
        assertTrue(frames.get(0).image().getWidth() >= 1);
        assertTrue(frames.get(0).image().getHeight() >= 1);
    }
}
