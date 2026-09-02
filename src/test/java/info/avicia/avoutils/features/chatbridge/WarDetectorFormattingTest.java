package info.avicia.avoutils.features.chatbridge;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarDetectorFormattingTest {

    @Test
    void formatNumberScalesLargeValues() throws Exception {
        assertEquals("999", formatNumber(999));
        assertEquals("1.0k", formatNumber(1_000));
        assertEquals("1.5k", formatNumber(1_500));
        assertEquals("1.0M", formatNumber(1_000_000));
        assertEquals("2.5M", formatNumber(2_500_000));
    }

    @Test
    void formatDurationUsesAppropriateUnits() throws Exception {
        assertEquals("0s", formatDuration(0));
        assertEquals("45s", formatDuration(45));
        assertEquals("1m 30s", formatDuration(90));
        assertEquals("1h 1m", formatDuration(3_661));
    }

    private static String formatNumber(long value) throws Exception {
        Method method = WarDetector.class.getDeclaredMethod("formatNumber", long.class);
        method.setAccessible(true);
        return (String) method.invoke(null, value);
    }

    private static String formatDuration(long seconds) throws Exception {
        Method method = WarDetector.class.getDeclaredMethod("formatDuration", long.class);
        method.setAccessible(true);
        return (String) method.invoke(null, seconds);
    }
}
