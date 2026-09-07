package info.avicia.avoutils.features.emojis.animation;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified decoder for animated and static emoji image formats (GIF, APNG, PNG).
 */
public final class AnimatedImageDecoder {

    public static final int TARGET_SIZE = 32;
    public static final int MAX_FRAMES = 30;

    private AnimatedImageDecoder() {}

    public static List<AnimationFrame> decode(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return List.of();
        }

        List<AnimationFrame> rawFrames;
        if (GifDecoder.isGif(data)) {
            rawFrames = GifDecoder.decode(data);
        } else if (ApngDecoder.isApng(data)) {
            rawFrames = ApngDecoder.decode(data);
        } else {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img != null) {
                rawFrames = List.of(new AnimationFrame(img, 100));
            } else {
                return List.of();
            }
        }

        if (rawFrames.isEmpty()) {
            return List.of();
        }

        // Apply frame limit downsampling if necessary
        List<AnimationFrame> cappedFrames = capFrames(rawFrames, MAX_FRAMES);

        // Resize frames to target size (32x32) if needed
        List<AnimationFrame> scaledFrames = new ArrayList<>(cappedFrames.size());
        for (AnimationFrame frame : cappedFrames) {
            scaledFrames.add(new AnimationFrame(scaleToTarget(frame.image(), TARGET_SIZE), frame.delayMs()));
        }

        return scaledFrames;
    }

    /**
     * Stitches multiple frames vertically into a single sprite sheet image.
     */
    public static BufferedImage stitchVertically(List<AnimationFrame> frames) {
        if (frames == null || frames.isEmpty()) {
            return new BufferedImage(TARGET_SIZE, TARGET_SIZE, BufferedImage.TYPE_INT_ARGB);
        }

        int width = TARGET_SIZE;
        int height = TARGET_SIZE * frames.size();
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        try {
            for (int i = 0; i < frames.size(); i++) {
                g.drawImage(frames.get(i).image(), 0, i * TARGET_SIZE, null);
            }
        } finally {
            g.dispose();
        }

        normalizeFrameAdvances(sheet, frames.size());

        return sheet;
    }

    /**
     * Normalizes the rightmost non-transparent pixel boundary across all frames of an animated
     * sprite sheet. Sets an imperceptible anchor pixel (alpha = 1) at the maximum rightmost column
     * on frames that do not reach it, ensuring Minecraft's BitmapFont$Loader calculates identical
     * advance values for every frame and eliminates horizontal text jitter.
     *
     * @return true if any modifications were made to the image
     */
    public static boolean normalizeFrameAdvances(BufferedImage sheet, int frameCount) {
        if (sheet == null || frameCount <= 1 || sheet.getWidth() != TARGET_SIZE || sheet.getHeight() != TARGET_SIZE * frameCount) {
            return false;
        }

        int maxRightX = -1;
        for (int f = 0; f < frameCount; f++) {
            int yOffset = f * TARGET_SIZE;
            for (int x = TARGET_SIZE - 1; x > maxRightX; x--) {
                for (int y = 0; y < TARGET_SIZE; y++) {
                    int alpha = (sheet.getRGB(x, yOffset + y) >> 24) & 0xFF;
                    if (alpha > 0) {
                        maxRightX = x;
                        break;
                    }
                }
            }
        }

        if (maxRightX < 0) {
            return false;
        }

        boolean modified = false;
        for (int f = 0; f < frameCount; f++) {
            int yOffset = f * TARGET_SIZE;
            boolean hasPixelAtMax = false;
            for (int y = 0; y < TARGET_SIZE; y++) {
                int alpha = (sheet.getRGB(maxRightX, yOffset + y) >> 24) & 0xFF;
                if (alpha > 0) {
                    hasPixelAtMax = true;
                    break;
                }
            }
            if (!hasPixelAtMax) {
                // Set an anchor pixel with alpha=1 (1/255, imperceptible to human eye)
                // Minecraft's NativeImage.getOpacity() checks opacity != 0, so 1 is treated as non-transparent.
                sheet.setRGB(maxRightX, yOffset, 0x01000000);
                modified = true;
            }
        }

        return modified;
    }

    private static List<AnimationFrame> capFrames(List<AnimationFrame> frames, int max) {
        if (frames == null || frames.isEmpty() || max <= 0) {
            return List.of();
        }
        if (frames.size() <= max) {
            return frames;
        }

        List<AnimationFrame> sampled = new ArrayList<>(max);
        double step = (double) frames.size() / max;
        for (int i = 0; i < max; i++) {
            int index = (int) Math.round(i * step);
            if (index >= frames.size()) index = frames.size() - 1;
            AnimationFrame f = frames.get(index);
            // Multiply delay according to sample step so total animation duration stays accurate
            int adjustedDelay = (int) Math.round(f.delayMs() * step);
            sampled.add(new AnimationFrame(f.image(), adjustedDelay));
        }
        return sampled;
    }

    public static BufferedImage scaleToTarget(BufferedImage src, int targetSize) {
        if (src == null) {
            return null;
        }
        targetSize = Math.max(1, targetSize);
        if (src.getWidth() == targetSize && src.getHeight() == targetSize) {
            return src;
        }

        BufferedImage scaled = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Center proportionally if not square
            int w = Math.max(1, src.getWidth());
            int h = Math.max(1, src.getHeight());
            double scale = Math.min((double) targetSize / w, (double) targetSize / h);
            int newW = Math.max(1, (int) Math.round(w * scale));
            int newH = Math.max(1, (int) Math.round(h * scale));
            int x = (targetSize - newW) / 2;
            int y = (targetSize - newH) / 2;

            g.drawImage(src, x, y, newW, newH, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }
}
