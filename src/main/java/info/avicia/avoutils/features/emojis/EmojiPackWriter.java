package info.avicia.avoutils.features.emojis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import info.avicia.avoutils.features.emojis.animation.AnimationMeta;
import info.avicia.avoutils.features.emojis.models.FontConfig;
import info.avicia.avoutils.features.emojis.models.FontProvider;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Writes the emoji resource pack: its pack.mcmeta, and the font JSON that maps code points to emoji images. */
final class EmojiPackWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("[^a-zA-Z0-9_.-]");

    private final Path packDir;
    private final int packFormat;

    EmojiPackWriter(Path packDir, int packFormat) {
        this.packDir = packDir;
        this.packFormat = packFormat;
    }

    // ── Resource pack structure ──────────────────────────────────────────

    void createResourcePackStructure() throws IOException {
        Path mcmetaPath = packDir.resolve("pack.mcmeta");
        boolean needsUpdate = true;

        if (Files.exists(mcmetaPath)) {
            try {
                String raw = Files.readString(mcmetaPath, StandardCharsets.UTF_8);
                JsonObject rootObj = JsonParser.parseString(raw).getAsJsonObject();
                int existingFormat = rootObj.getAsJsonObject("pack").get("pack_format").getAsInt();
                if (existingFormat == packFormat) {
                    needsUpdate = false;
                }
            } catch (Exception ignored) {
            }
        }

        if (needsUpdate) {
            Files.createDirectories(packDir);
            PackMetadata pack = new PackMetadata(PackInfo.uniform(packFormat, "Dynamic emojis for AvoUtils"));
            Files.writeString(mcmetaPath, GSON.toJson(pack));
        }
    }

    boolean writeFontJson(Map<String, String> customEmojis, Map<Integer, AnimationMeta> animatedEmojis,
                          TwemojiManager twemojiManager) throws IOException {
        Path defaultFontPath = packDir.resolve("assets/minecraft/font/default.json");
        Files.createDirectories(defaultFontPath.getParent());

        FontConfig defaultFontConfig = new FontConfig();

        synchronized (customEmojis) {
            List<Map.Entry<String, String>> sortedEntries = new ArrayList<>(customEmojis.entrySet());
            sortedEntries.sort(Map.Entry.comparingByKey());

            for (Map.Entry<String, String> entry : sortedEntries) {
                String fullTrigger = entry.getKey();
                String name = fullTrigger.substring(1, fullTrigger.length() - 1);
                String safeName = safeNameFor(name);
                String unicodeStr = entry.getValue();
                int baseCp = unicodeStr.codePointAt(0);

                FontProvider provider = new FontProvider();
                provider.file = "avoutils:font/" + safeName + ".png";

                AnimationMeta anim = animatedEmojis.get(baseCp);
                if (anim != null && anim.frameCount() > 1) {
                    for (int f = 0; f < anim.frameCount(); f++) {
                        provider.chars.add(Character.toString(baseCp + f));
                    }
                } else {
                    provider.chars.add(unicodeStr);
                }
                defaultFontConfig.providers.add(provider);
            }
        }

        FontConfig standardConfig = twemojiManager.standardFontConfig;
        if (standardConfig != null && standardConfig.providers != null) {
            for (FontProvider prov : standardConfig.providers) {
                FontProvider defaultProv = new FontProvider();
                defaultProv.type = prov.type;
                defaultProv.file = prov.file;
                defaultProv.ascent = prov.ascent;
                defaultProv.height = prov.height;

                Map<Integer, String> charToPua = twemojiManager.standardCharToPua;
                for (String row : prov.chars) {
                    StringBuilder sbDefault = new StringBuilder();
                    int i = 0;
                    while (i < row.length()) {
                        int codePoint = row.codePointAt(i);
                        int charCount = Character.charCount(codePoint);
                        if (codePoint > 32) {
                            String puaStr = charToPua.get(codePoint);
                            if (puaStr != null) {
                                sbDefault.append(puaStr);
                            } else {
                                sbDefault.append('\u0000');
                            }
                        } else {
                            sbDefault.append(row, i, i + charCount);
                        }
                        i += charCount;
                    }
                    defaultProv.chars.add(sbDefault.toString());
                }
                defaultFontConfig.providers.add(defaultProv);
            }
        }

        String newJson = GSON.toJson(defaultFontConfig);
        boolean contentChanged = true;
        if (Files.exists(defaultFontPath)) {
            try {
                String existing = Files.readString(defaultFontPath, StandardCharsets.UTF_8);
                if (existing.equals(newJson)) {
                    contentChanged = false;
                }
            } catch (Exception ignored) {
            }
        }

        if (contentChanged) {
            Path tempPath = defaultFontPath.resolveSibling("default.json.tmp");
            try {
                Files.writeString(tempPath, newJson, StandardCharsets.UTF_8);
                Files.move(tempPath, defaultFontPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (Exception ignored) {
                }
            }
        }
        return contentChanged;
    }

    /*
     * Sanitizes an emoji name for use as a resource-pack file name
     */
    static String safeNameFor(String name) {
        return SAFE_NAME_PATTERN.matcher(name).replaceAll("_").toLowerCase(Locale.ROOT);
    }

    // ── Pack format resolution ───────────────────────────────────────────

    static int resolvePackFormat() {
        try {
            return currentPackFormat();
        } catch (IllegalStateException e) {
            // The game version isn't set yet; 75 is 1.21.11's resource pack format
            return 75;
        }
    }

    /**
     * The running game's resource pack format.
     */
    static int currentPackFormat() {
        return SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).major();
    }

    // ── pack.mcmeta structures ───────────────────────────────────────────

    private record PackMetadata(PackInfo pack) {
    }

    private record PackInfo(int pack_format, int min_format, int max_format, String description) {
        static PackInfo uniform(int format, String description) {
            return new PackInfo(format, format, format, description);
        }
    }
}

