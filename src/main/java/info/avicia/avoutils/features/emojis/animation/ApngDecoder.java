package info.avicia.avoutils.features.emojis.animation;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Decodes APNG (Animated PNG) files into individual AnimationFrames with compositing and timing.
 */
public final class ApngDecoder {

    private static final byte[] PNG_SIGNATURE = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private ApngDecoder() {}

    public static boolean isApng(byte[] data) {
        if (!isPng(data)) return false;
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            if (dis.skipBytes(8) != 8) return false;
            byte[] typeBytes = new byte[4];
            while (dis.available() >= 8) {
                int length = dis.readInt();
                if (length < 0 || length > dis.available() - 8) {
                    return false;
                }
                dis.readFully(typeBytes);
                if (typeBytes[0] == 'a' && typeBytes[1] == 'c' && typeBytes[2] == 'T' && typeBytes[3] == 'L') {
                    return true;
                }
                if ((typeBytes[0] == 'I' && typeBytes[1] == 'D' && typeBytes[2] == 'A' && typeBytes[3] == 'T')
                        || (typeBytes[0] == 'I' && typeBytes[1] == 'E' && typeBytes[2] == 'N' && typeBytes[3] == 'D')) {
                    return false;
                }
                int toSkip = length + 4;
                if (dis.skipBytes(toSkip) != toSkip) {
                    return false;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean isPng(byte[] data) {
        if (data == null || data.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) return false;
        }
        return true;
    }

    public static List<AnimationFrame> decode(byte[] data) throws IOException {
        if (!isPng(data)) {
            return List.of();
        }
        List<Chunk> chunks = readChunks(data);
        Chunk ihdrChunk = null;
        Chunk actlChunk = null;
        List<Chunk> paletteChunks = new ArrayList<>();

        for (Chunk chunk : chunks) {
            if ("IHDR".equals(chunk.type)) ihdrChunk = chunk;
            else if ("acTL".equals(chunk.type)) actlChunk = chunk;
            else if ("PLTE".equals(chunk.type) || "tRNS".equals(chunk.type)) paletteChunks.add(chunk);
        }

        if (ihdrChunk == null || actlChunk == null) {
            // Not animated APNG, return single static frame
            BufferedImage staticImg = ImageIO.read(new ByteArrayInputStream(data));
            if (staticImg != null) {
                return List.of(new AnimationFrame(staticImg, 100));
            }
            return List.of();
        }

        DataInputStream ihdrStream = new DataInputStream(new ByteArrayInputStream(ihdrChunk.data));
        int canvasWidth = Math.max(1, ihdrStream.readInt());
        int canvasHeight = Math.max(1, ihdrStream.readInt());
        byte bitDepth = ihdrStream.readByte();
        byte colorType = ihdrStream.readByte();
        byte compression = ihdrStream.readByte();
        byte filter = ihdrStream.readByte();
        byte interlace = ihdrStream.readByte();

        // Group chunks by frame
        List<FrameControl> frameControls = new ArrayList<>();
        List<List<Chunk>> frameDataChunks = new ArrayList<>();
        FrameControl currentFcTL = null;
        List<Chunk> currentData = new ArrayList<>();

        for (Chunk chunk : chunks) {
            if ("fcTL".equals(chunk.type)) {
                if (currentFcTL != null && !currentData.isEmpty()) {
                    frameControls.add(currentFcTL);
                    frameDataChunks.add(currentData);
                    currentData = new ArrayList<>();
                }
                currentFcTL = parseFcTL(chunk.data);
            } else if ("IDAT".equals(chunk.type) || "fdAT".equals(chunk.type)) {
                currentData.add(chunk);
            }
        }
        if (currentFcTL != null && !currentData.isEmpty()) {
            frameControls.add(currentFcTL);
            frameDataChunks.add(currentData);
        }

        if (frameControls.isEmpty()) {
            BufferedImage staticImg = ImageIO.read(new ByteArrayInputStream(data));
            if (staticImg != null) return List.of(new AnimationFrame(staticImg, 100));
            return List.of();
        }

        BufferedImage master = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D masterGraphics = master.createGraphics();
        BufferedImage prevMaster = null;
        List<AnimationFrame> result = new ArrayList<>();

        try {
            for (int i = 0; i < frameControls.size(); i++) {
                FrameControl fc = frameControls.get(i);
                List<Chunk> dataChunks = frameDataChunks.get(i);

                byte[] framePng = buildSubPng(fc.width, fc.height, bitDepth, colorType, compression, filter, interlace,
                        paletteChunks, dataChunks);
                BufferedImage frameImage = ImageIO.read(new ByteArrayInputStream(framePng));
                if (frameImage == null) continue;

                if (fc.disposeOp == 2) { // RESTORE_PREVIOUS
                    prevMaster = copyImage(master);
                }

                if (fc.blendOp == 0) { // SOURCE: overwrite rectangle
                    masterGraphics.setComposite(AlphaComposite.Src);
                } else { // OVER: alpha composite over
                    masterGraphics.setComposite(AlphaComposite.SrcOver);
                }

                masterGraphics.drawImage(frameImage, fc.xOffset, fc.yOffset, null);
                result.add(new AnimationFrame(copyImage(master), fc.delayMs));

                if (fc.disposeOp == 1) { // RESTORE_BACKGROUND: clear rectangle
                    masterGraphics.setComposite(AlphaComposite.Clear);
                    masterGraphics.fillRect(fc.xOffset, fc.yOffset, fc.width, fc.height);
                } else if (fc.disposeOp == 2 && prevMaster != null) { // RESTORE_PREVIOUS
                    master = copyImage(prevMaster);
                    masterGraphics.dispose();
                    masterGraphics = master.createGraphics();
                }
            }
        } finally {
            masterGraphics.dispose();
        }
        return result;
    }

    private static byte[] buildSubPng(int width, int height, byte bitDepth, byte colorType,
                                      byte compression, byte filter, byte interlace,
                                      List<Chunk> paletteChunks, List<Chunk> dataChunks) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(PNG_SIGNATURE);

        // Build IHDR
        ByteArrayOutputStream ihdrBaos = new ByteArrayOutputStream();
        DataOutputStream ihdrDos = new DataOutputStream(ihdrBaos);
        ihdrDos.writeInt(width);
        ihdrDos.writeInt(height);
        ihdrDos.writeByte(bitDepth);
        ihdrDos.writeByte(colorType);
        ihdrDos.writeByte(compression);
        ihdrDos.writeByte(filter);
        ihdrDos.writeByte(interlace);
        writeChunk(baos, "IHDR", ihdrBaos.toByteArray());

        // Palette
        for (Chunk p : paletteChunks) {
            writeChunk(baos, p.type, p.data);
        }

        // IDAT chunks
        for (Chunk d : dataChunks) {
            if ("IDAT".equals(d.type)) {
                writeChunk(baos, "IDAT", d.data);
            } else if ("fdAT".equals(d.type) && d.data.length >= 4) {
                // Skip 4-byte sequence number
                byte[] idatData = Arrays.copyOfRange(d.data, 4, d.data.length);
                writeChunk(baos, "IDAT", idatData);
            }
        }

        // IEND
        writeChunk(baos, "IEND", new byte[0]);
        return baos.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream baos, String type, byte[] data) throws IOException {
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        dos.write(typeBytes);
        dos.write(data);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        dos.writeInt((int) crc.getValue());
    }

    private static FrameControl parseFcTL(byte[] data) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        dis.readInt(); // sequence
        int width = Math.max(1, dis.readInt());
        int height = Math.max(1, dis.readInt());
        int xOffset = Math.max(0, dis.readInt());
        int yOffset = Math.max(0, dis.readInt());
        int delayNum = dis.readUnsignedShort();
        int delayDen = dis.readUnsignedShort();
        if (delayDen == 0) delayDen = 100;
        int delayMs = (int) Math.round((double) delayNum * 1000.0 / delayDen);
        if (delayMs <= 10) delayMs = 100;

        byte disposeOp = dis.readByte();
        byte blendOp = dis.readByte();

        return new FrameControl(width, height, xOffset, yOffset, delayMs, disposeOp, blendOp);
    }

    private static List<Chunk> readChunks(byte[] data) throws IOException {
        List<Chunk> chunks = new ArrayList<>();
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        byte[] sig = new byte[8];
        dis.readFully(sig);

        while (dis.available() >= 8) {
            int length = dis.readInt();
            if (length < 0 || length > dis.available() - 8) {
                throw new IOException("Malformed PNG chunk length: " + length);
            }
            byte[] typeBytes = new byte[4];
            dis.readFully(typeBytes);
            String type = new String(typeBytes, StandardCharsets.US_ASCII);
            byte[] chunkData = new byte[length];
            dis.readFully(chunkData);
            dis.readInt(); // CRC
            chunks.add(new Chunk(type, chunkData));
            if ("IEND".equals(type)) break;
        }
        return chunks;
    }

    private static BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    private record Chunk(String type, byte[] data) {}

    private record FrameControl(int width, int height, int xOffset, int yOffset,
                                int delayMs, byte disposeOp, byte blendOp) {}
}
