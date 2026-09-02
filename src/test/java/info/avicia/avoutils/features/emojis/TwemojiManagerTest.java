package info.avicia.avoutils.features.emojis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TwemojiManagerTest {

    @Test
    void walkStringCodePointsCollectsCodePointsAboveSpace() {
        List<Integer> collected = new ArrayList<>();
        TwemojiManager.walkStringCodePoints("ab", collected::add);
        assertEquals(List.of(97, 98), collected);
    }

    @Test
    void walkStringCodePointsSkipsSpacesAndHandlesSurrogatePairs() {
        List<Integer> collected = new ArrayList<>();
        TwemojiManager.walkStringCodePoints("a \uD83D\uDE00b", collected::add);
        // 'a', U+1F600 (surrogate pair), 'b'; space (32) skipped
        assertEquals(List.of(97, 0x1F600, 98), collected);
    }

    @Test
    void walkProviderCharsWalksEveryRow() {
        FontProvider provider = new FontProvider();
        provider.chars.add("ab");
        provider.chars.add("\uD83D\uDE00");

        List<Integer> collected = new ArrayList<>();
        TwemojiManager.walkProviderChars(provider, collected::add);

        assertEquals(List.of(97, 98, 0x1F600), collected);
    }
}
