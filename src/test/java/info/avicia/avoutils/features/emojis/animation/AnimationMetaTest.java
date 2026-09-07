package info.avicia.avoutils.features.emojis.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationMetaTest {

    @Test
    void animationMetaCalculatesTotalDuration() {
        int[] delays = new int[]{100, 200, 300};
        AnimationMeta meta = new AnimationMeta(0xF0000, delays);

        assertEquals(3, meta.frameCount());
        assertEquals(600, meta.totalDurationMs());
        assertEquals(0xF0000, meta.baseCodePoint());
    }

    @Test
    void getFrameIndexRespectsVariableDelays() {
        int[] delays = new int[]{100, 200, 300}; // Frame 0: 0-99ms, Frame 1: 100-299ms, Frame 2: 300-599ms
        AnimationMeta meta = new AnimationMeta(0xF0000, delays);

        assertEquals(0, meta.getFrameIndex(0));
        assertEquals(0, meta.getFrameIndex(50));
        assertEquals(0, meta.getFrameIndex(99));

        assertEquals(1, meta.getFrameIndex(100));
        assertEquals(1, meta.getFrameIndex(200));
        assertEquals(1, meta.getFrameIndex(299));

        assertEquals(2, meta.getFrameIndex(300));
        assertEquals(2, meta.getFrameIndex(500));
        assertEquals(2, meta.getFrameIndex(599));

        // Loops around at 600ms
        assertEquals(0, meta.getFrameIndex(600));
        assertEquals(1, meta.getFrameIndex(700));
        assertEquals(2, meta.getFrameIndex(900));
    }

    @Test
    void getActiveCodePointOffsetsFromBase() {
        int[] delays = new int[]{100, 100};
        AnimationMeta meta = new AnimationMeta(0xF0100, delays);

        assertEquals(0xF0100, meta.getActiveCodePoint(50));
        assertEquals(0xF0101, meta.getActiveCodePoint(150));
        assertEquals(0xF0100, meta.getActiveCodePoint(250));
    }

    @Test
    void handlesSingleFrameAndZeroDurationGracefully() {
        AnimationMeta single = new AnimationMeta(0xF0000, 1, new int[]{0}, 0);
        assertEquals(0, single.getFrameIndex(1000));
        assertEquals(0xF0000, single.getActiveCodePoint(1000));

        AnimationMeta empty = new AnimationMeta(0xF0000, 0, new int[0], 0);
        assertEquals(0, empty.getFrameIndex(500));

        AnimationMeta fromNull = new AnimationMeta(0xF0000, null);
        assertEquals(0, fromNull.frameCount());
        assertEquals(0, fromNull.totalDurationMs());
        assertEquals(0, fromNull.getFrameIndex(500));
        assertEquals(0xF0000, fromNull.getActiveCodePoint(500));
    }
}
