package info.avicia.avoutils.features.wardetector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarDetectorMembershipTest {

    @BeforeEach
    @AfterEach
    void cleanState() {
        WarDetector.reset();
    }

    @Test
    void resetClearsAllActiveWarState() throws Exception {
        setStaticField("activeBattleId", "Detlas:1000");
        setStaticField("activeTerritory", "Detlas");
        setStaticField("submissionSent", true);
        Set<String> warrers = getActiveWarrersField();
        warrers.add("Steve");
        warrers.add("Alex");

        assertEquals("Detlas:1000", WarDetector.getActiveBattleId());
        assertEquals(2, WarDetector.getActiveWarrers().size());
        assertTrue(WarDetector.isSubmissionSent());

        WarDetector.reset();

        assertNull(WarDetector.getActiveBattleId());
        assertNull(getStaticField("activeTerritory"));
        assertTrue(WarDetector.getActiveWarrers().isEmpty());
        assertFalse(WarDetector.isSubmissionSent());
    }

    @Test
    void sanitizeWarrersFiltersInvalidAndDeduplicates() throws Exception {
        Method method = WarDetector.class.getDeclaredMethod("sanitizeWarrers", Collection.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(
                null,
                List.of(" Steve ", "Alex", "bad name with spaces", "ab", "TooLongUsername12345678", "Steve", "Alex", "Valid_123")
        );

        assertEquals(List.of("Steve", "Alex", "Valid_123"), result);
    }

    @Test
    void sanitizeWarrersHandlesEmptyAndNullCollections() throws Exception {
        Method method = WarDetector.class.getDeclaredMethod("sanitizeWarrers", Collection.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> nullResult = (List<String>) method.invoke(null, (Collection<String>) null);
        assertTrue(nullResult.isEmpty());

        @SuppressWarnings("unchecked")
        List<String> emptyResult = (List<String>) method.invoke(null, List.of());
        assertTrue(emptyResult.isEmpty());
    }

    @Test
    void tryDetectOutcomeReturnsNullWhenNoActiveBattle() {
        assertNull(WarDetector.tryDetectOutcome("Territory Captured! Captured \"Detlas Suburbs\""));
        assertNull(WarDetector.tryDetectOutcome("You lost the war for Detlas Suburbs!"));
    }

    @Test
    void tryDetectOutcomeReturnsNullWhenSubmissionAlreadySent() throws Exception {
        setStaticField("activeBattleId", "Detlas:1000");
        setStaticField("submissionSent", true);

        assertNull(WarDetector.tryDetectOutcome("Territory Captured!"));
    }

    @Test
    void tryDetectOutcomeIgnoresUnrelatedChat() throws Exception {
        setStaticField("activeBattleId", "Detlas:1000");

        assertNull(WarDetector.tryDetectOutcome("Good luck with the war everyone!"));
        assertNull(WarDetector.tryDetectOutcome("Random player joined the game"));
    }

    @Test
    void activeWarrersAccumulatesAcrossMultipleUpdates() throws Exception {
        Set<String> warrers = getActiveWarrersField();
        warrers.add("PlayerOne");
        assertEquals(Set.of("PlayerOne"), WarDetector.getActiveWarrers());

        // Late joiner joins
        warrers.add("PlayerTwo");
        assertEquals(Set.of("PlayerOne", "PlayerTwo"), WarDetector.getActiveWarrers());

        // Duplicate joiner ignored
        warrers.add("PlayerOne");
        assertEquals(Set.of("PlayerOne", "PlayerTwo"), WarDetector.getActiveWarrers());
    }

    @Test
    void tryDetectOutcomeHandlesNull() {
        assertNull(WarDetector.tryDetectOutcome(null));
    }

    private static void setStaticField(String fieldName, Object value) throws Exception {
        Field field = WarDetector.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static Object getStaticField(String fieldName) throws Exception {
        Field field = WarDetector.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> getActiveWarrersField() throws Exception {
        Field field = WarDetector.class.getDeclaredField("activeWarrers");
        field.setAccessible(true);
        return (Set<String>) field.get(null);
    }
}
