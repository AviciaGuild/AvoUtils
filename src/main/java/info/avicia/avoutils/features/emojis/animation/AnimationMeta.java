package info.avicia.avoutils.features.emojis.animation;

/**
 * Metadata describing an animated emoji's timing and code point range.
 *
 * @param baseCodePoint   The first Plane 15 code point (Frame 0)
 * @param frameCount      Number of frames
 * @param frameDelays     Array of millisecond delays for each frame
 * @param totalDurationMs Sum of all frame delays in milliseconds
 */
public record AnimationMeta(int baseCodePoint, int frameCount, int[] frameDelays, int totalDurationMs) {

    public AnimationMeta(int baseCodePoint, int[] frameDelays) {
        this(baseCodePoint,
                frameDelays == null ? 0 : frameDelays.length,
                frameDelays == null ? new int[0] : frameDelays,
                frameDelays == null ? 0 : calculateTotal(frameDelays));
    }

    private static int calculateTotal(int[] delays) {
        if (delays == null) return 0;
        int sum = 0;
        for (int d : delays) {
            sum += Math.max(10, d);
        }
        return sum;
    }

    public int getFrameIndex(long timeMs) {
        if (totalDurationMs <= 0 || frameCount <= 1 || frameDelays == null || frameDelays.length == 0) {
            return 0;
        }
        long elapsed = timeMs % totalDurationMs;
        if (elapsed < 0) elapsed += totalDurationMs;

        long running = 0;
        for (int i = 0; i < frameDelays.length; i++) {
            running += Math.max(10, frameDelays[i]);
            if (elapsed < running) {
                return i;
            }
        }
        return 0;
    }

    public int getActiveCodePoint(long timeMs) {
        return baseCodePoint + getFrameIndex(timeMs);
    }
}
