package info.avicia.avoutils.features.emojis.animation;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class ApngDecoderTest {

    @Test
    void isPngDetectsPngSignature() {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertTrue(ApngDecoder.isPng(png));
        assertFalse(ApngDecoder.isPng("GIF89a".getBytes()));
        assertFalse(ApngDecoder.isPng(null));
        assertFalse(ApngDecoder.isPng(new byte[]{1, 2, 3}));
    }

    @Test
    void isApngDetectsActlChunk() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

        // Write an acTL chunk
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(8); // length
        byte[] type = "acTL".getBytes(StandardCharsets.US_ASCII);
        dos.write(type);
        byte[] data = new byte[]{0, 0, 0, 2, 0, 0, 0, 0}; // 2 frames, 0 plays
        dos.write(data);

        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(data);
        dos.writeInt((int) crc.getValue());

        byte[] apngBytes = baos.toByteArray();
        assertTrue(ApngDecoder.isApng(apngBytes));
    }

    @Test
    void decodeStaticPngReturnsSingleFrame() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);

        List<AnimationFrame> frames = ApngDecoder.decode(baos.toByteArray());
        assertEquals(1, frames.size());
        assertEquals(16, frames.get(0).image().getWidth());
    }

    @Test
    void isApngReturnsFalseForStaticPng() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);

        assertFalse(ApngDecoder.isApng(baos.toByteArray()));
    }

    @Test
    void isApngReturnsFalseForCorruptOrMalformedChunks() {
        byte[] malformed = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x7F, 0x7F, 0x7F, 0x7F};
        assertFalse(ApngDecoder.isApng(malformed));
        assertFalse(ApngDecoder.isApng(new byte[]{1, 2, 3}));
        assertFalse(ApngDecoder.isApng(null));
    }

    @Test
    void decodeReturnsEmptyOnNonPng() throws Exception {
        assertTrue(ApngDecoder.decode(new byte[]{1, 2, 3}).isEmpty());
        assertTrue(ApngDecoder.decode(null).isEmpty());
    }
}
