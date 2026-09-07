package info.avicia.avoutils.features.emojis;

import com.google.gson.Gson;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.features.emojis.animation.AnimationMeta;
import info.avicia.avoutils.features.emojis.models.FontConfig;
import info.avicia.avoutils.features.emojis.models.FontProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmojiFeatureTest {

    @TempDir
    Path tempDir;

    @Test
    void safeNameForLowercasesAndReplacesInvalidCharacters() {
        assertEquals("hello_world_", EmojiFeature.safeNameFor("Hello World!"));
        assertEquals("cupboi", EmojiFeature.safeNameFor("CupBoi"));
    }

    @Test
    void safeNameForKeepsAllowedCharacters() {
        assertEquals("a.b-c_d", EmojiFeature.safeNameFor("a.b-c_d"));
    }

    @Test
    void buildTrieCombinesStandardAndCustomEmojis() {
        Map<String, String> standard = new HashMap<>();
        standard.put(":smile:", "PUA-1");
        Map<String, String> custom = new HashMap<>();
        custom.put(":avicia:", "PUA-2");

        EmojiTrie trie = EmojiFeature.buildTrie(standard, custom);

        assertEquals("PUA-1", trie.search(":smile:", 0, 7));
        assertEquals("PUA-2", trie.search(":avicia:", 0, 8));
        assertFalse(trie.isEmpty());
    }

    @Test
    void getAnimatedFrameCodePointReturnsBaseWhenDisabled() {
        TwemojiManager twemojiManager = new TwemojiManager(tempDir, 75);
        EmojiFeature feature = new EmojiFeature(twemojiManager, tempDir, 75);
        ModConfig config = new ModConfig();
        feature.setConfig(config);

        int baseCp = 0xF0000;
        AnimationMeta meta = new AnimationMeta(baseCp, new int[]{100, 100});
        feature.getAnimatedEmojis().put(baseCp, meta);

        // Disabled via emojiEnabled = false
        config.emojiEnabled = false;
        assertEquals(baseCp, feature.getAnimatedFrameCodePoint(baseCp));

        // Disabled when config is null
        feature.setConfig(null);
        assertEquals(baseCp, feature.getAnimatedFrameCodePoint(baseCp));
    }

    @Test
    void getAnimatedFrameCodePointResolvesFrameOffsetWhenEnabled() {
        TwemojiManager twemojiManager = new TwemojiManager(tempDir, 75);
        EmojiFeature feature = new EmojiFeature(twemojiManager, tempDir, 75);
        ModConfig config = new ModConfig();
        config.emojiEnabled = true;
        feature.setConfig(config);

        int baseCp = 0xF0010;
        AnimationMeta meta = new AnimationMeta(baseCp, new int[]{100, 100, 100});
        feature.getAnimatedEmojis().put(baseCp, meta);

        int activeCp = feature.getAnimatedFrameCodePoint(baseCp);
        assertTrue(activeCp >= baseCp && activeCp <= baseCp + 2);

        // Non-animated codepoints pass through unchanged
        int nonAnimated = 0xF0050;
        assertEquals(nonAnimated, feature.getAnimatedFrameCodePoint(nonAnimated));
    }

    @Test
    void writeFontJsonEmitsMultiRowCharsForAnimatedEmotes() throws IOException {
        TwemojiManager twemojiManager = new TwemojiManager(tempDir, 75);
        EmojiFeature feature = new EmojiFeature(twemojiManager, tempDir, 75);

        int staticCp = 0xF0000;
        int animCp = 0xF0001;

        feature.getCustomEmojis().put(":static:", Character.toString(staticCp));
        feature.getCustomEmojis().put(":party:", Character.toString(animCp));

        // Register :party: with 3 frames
        AnimationMeta animMeta = new AnimationMeta(animCp, new int[]{50, 50, 50});
        feature.getAnimatedEmojis().put(animCp, animMeta);

        boolean changed = feature.writeFontJsonForTesting();
        assertTrue(changed);

        Path fontJsonPath = tempDir.resolve("assets/minecraft/font/default.json");
        assertTrue(Files.exists(fontJsonPath));

        String json = Files.readString(fontJsonPath, StandardCharsets.UTF_8);
        FontConfig parsed = new Gson().fromJson(json, FontConfig.class);
        assertNotNull(parsed);
        assertEquals(2, parsed.providers.size());

        FontProvider staticProvider = parsed.providers.stream()
                .filter(p -> p.file.equals("avoutils:font/static.png"))
                .findFirst().orElseThrow();
        assertEquals(1, staticProvider.chars.size());
        assertEquals(Character.toString(staticCp), staticProvider.chars.get(0));

        FontProvider animProvider = parsed.providers.stream()
                .filter(p -> p.file.equals("avoutils:font/party.png"))
                .findFirst().orElseThrow();
        assertEquals(3, animProvider.chars.size());
        assertEquals(Character.toString(animCp), animProvider.chars.get(0));
        assertEquals(Character.toString(animCp + 1), animProvider.chars.get(1));
        assertEquals(Character.toString(animCp + 2), animProvider.chars.get(2));
    }

    @Test
    void manifestSavesAndLoadsCorrectly() {
        TwemojiManager twemojiManager = new TwemojiManager(tempDir, 75);
        EmojiFeature feature = new EmojiFeature(twemojiManager, tempDir, 75);

        Map<String, String> urls = new HashMap<>();
        urls.put("Avicia", "https://example.com/avicia.png");
        urls.put("party", "https://example.com/party.gif");

        EmojiFeature.EmojiManifest manifest = new EmojiFeature.EmojiManifest("abc123hash", urls);
        feature.saveManifestForTesting(manifest);

        assertTrue(Files.exists(feature.getManifestPath()));

        EmojiFeature.EmojiManifest loaded = feature.loadManifestForTesting();
        assertNotNull(loaded);
        assertEquals("abc123hash", loaded.hash());
        assertEquals(2, loaded.urls().size());
        assertEquals("https://example.com/avicia.png", loaded.urls().get("Avicia"));
        assertEquals("https://example.com/party.gif", loaded.urls().get("party"));
    }

    @Test
    void matchShortcodesDeduplicatesStandardAndCustomOverlap() {
        Set<String> custom = Set.of(":fire:", ":custom_star:");
        Set<String> standard = Set.of(":fire:", ":star:");

        List<String> matches = EmojiFeature.matchShortcodes(custom, standard, ":");
        assertEquals(3, matches.size());
        assertTrue(matches.contains(":fire:"));
        assertTrue(matches.contains(":custom_star:"));
        assertTrue(matches.contains(":star:"));
        // Assert :fire: is only present once
        assertEquals(1, matches.stream().filter(s -> s.equals(":fire:")).count());
    }
}
