package info.avicia.avoutils.features.emojis.animation;

import java.awt.image.BufferedImage;

/**
 * Represents a single frame in an animated image.
 *
 * @param image   The frame's decoded image
 * @param delayMs The frame's display duration in milliseconds
 */
public record AnimationFrame(BufferedImage image, int delayMs) {
    public AnimationFrame {
        if (delayMs <= 0) {
            delayMs = 100; // Default to 10 FPS (100ms) if invalid/zero
        }
    }
}
