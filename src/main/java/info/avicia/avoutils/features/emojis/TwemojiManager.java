package info.avicia.avoutils.features.emojis;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import info.avicia.avoutils.AvoUtilsMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Downloads and parses the PixelTwemoji resource pack.
 * Extracts PUA code-point mappings and shortcode-to-emoji associations.
 */
class TwemojiManager {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private static final String TWEMOJI_ZIP_URL =
            "https://github.com/AmberWat/PixelTwemojiMC-18/releases/download/v8.0/PixelTwemojiMC-18.zip";
    static final String RAW_JSON_PATH = "assets/twemoji/font/emoji_raw.json";
    static final String SHORTCODES_JSON_PATH = "assets/emoji_shortcodes/lang/en_us.json";

    private final Path twemojiPath;
    private final int packFormat;
    private final AtomicBoolean downloading = new AtomicBoolean(false);

    final Map<String, String> standardEmojis = new HashMap<>();
    final Map<Integer, String> standardCharToPua = new HashMap<>();
    FontConfig standardFontConfig;

    TwemojiManager(Path gameDir, int packFormat) {
        this.twemojiPath = gameDir.resolve("avoutils/emojis/avoutils-twemoji.zip");
        this.packFormat = packFormat;
    }

    boolean exists() {
        return Files.exists(twemojiPath);
    }

    // ── Download ─────────────────────────────────────────────────────────

    void download() {
        if (Files.exists(twemojiPath)) {
            return;
        }
        if (!downloading.compareAndSet(false, true)) {
            return;
        }

        Path tempPath = twemojiPath.resolveSibling("avoutils-twemoji.tmp");
        AvoUtilsMod.LOGGER.info("Downloading Twemoji resource pack for high-quality color emojis...");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TWEMOJI_ZIP_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "AvoUtils Mod")
                    .build();
            HttpResponse<InputStream> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 200) {
                Files.createDirectories(twemojiPath.getParent());
                try (InputStream in = response.body()) {
                    Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
                }
                sanitize(tempPath, twemojiPath);
                Files.deleteIfExists(tempPath);
                AvoUtilsMod.LOGGER.info("Twemoji resource pack downloaded and sanitized successfully.");
            } else {
                AvoUtilsMod.LOGGER.error("Failed to download Twemoji pack. HTTP code: {}",
                        response.statusCode());
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Error downloading Twemoji resource pack", e);
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
        } finally {
            downloading.set(false);
        }
    }

    // ── Load / Parse ─────────────────────────────────────────────────────

    void loadResources() {
        if (!Files.exists(twemojiPath)) {
            AvoUtilsMod.LOGGER.error("Twemoji resource pack not found, standard emojis mapping skipped!");
            return;
        }

        try (ZipFile zip = new ZipFile(twemojiPath.toFile())) {
            loadPuaMappings(zip);
            loadStandardEmojis(zip);
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Failed to load standard emoji resources from ZIP", e);
        }
    }

    private void loadPuaMappings(ZipFile zip) {
        standardCharToPua.clear();
        ZipEntry entry = zip.getEntry(RAW_JSON_PATH);
        if (entry == null)
            return;

        try (InputStream is = zip.getInputStream(entry);
                InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            standardFontConfig = new Gson().fromJson(isr, FontConfig.class);
            if (standardFontConfig != null && standardFontConfig.providers != null) {
                for (FontProvider prov : standardFontConfig.providers) {
                    walkProviderChars(prov, codePoint -> {
                        standardCharToPua.putIfAbsent(codePoint,
                                String.valueOf((char) (0xE200 + standardCharToPua.size())));
                    });
                }
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Failed to load Twemoji PUA mappings", e);
        }
    }

    private void loadStandardEmojis(ZipFile zip) {
        ZipEntry entry = zip.getEntry(SHORTCODES_JSON_PATH);
        if (entry == null) {
            AvoUtilsMod.LOGGER.error("en_us.json not found in Twemoji pack!");
            return;
        }
        try (InputStream is = zip.getInputStream(entry);
                InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(isr).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entrySet : root.entrySet()) {
                String shortcode = entrySet.getKey();
                String value = entrySet.getValue().getAsString();
                if (value.isEmpty())
                    continue;
                StringBuilder puaBuilder = new StringBuilder();
                walkStringCodePoints(value, codePoint -> {
                    String puaChar = standardCharToPua.get(codePoint);
                    if (puaChar != null) {
                        puaBuilder.append(puaChar);
                    }
                });
                if (puaBuilder.length() > 0) {
                    standardEmojis.put(shortcode, puaBuilder.toString());
                }
            }
            AvoUtilsMod.LOGGER.info("Successfully loaded {} standard Twemoji shortcodes from ZIP.",
                    standardEmojis.size());
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("Failed to parse Twemoji shortcodes from ZIP", e);
        }
    }

    // ── ZIP sanitization ─────────────────────────────────────────────────

    private void sanitize(Path source, Path destination) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(source));
                ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(destination))) {
            ZipEntry entry;
            byte[] buffer = new byte[16384];
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (name.equals("assets/minecraft/font/default.json")) {
                    name = RAW_JSON_PATH;
                }

                if (name.equals("pack.mcmeta")) {
                    String raw = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    try {
                        JsonObject rootObj = JsonParser.parseString(raw).getAsJsonObject();
                        if (rootObj.has("pack")) {
                            JsonObject packObj = rootObj.getAsJsonObject("pack");
                            packObj.addProperty("pack_format", packFormat);
                            packObj.addProperty("min_format", packFormat);
                            packObj.addProperty("max_format", packFormat);
                            packObj.remove("supported_formats");
                        }
                        raw = new Gson().toJson(rootObj);
                    } catch (Exception ignored) {
                    }

                    ZipEntry newEntry = new ZipEntry(name);
                    zos.putNextEntry(newEntry);
                    zos.write(raw.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                    continue;
                }

                ZipEntry newEntry = new ZipEntry(name);
                zos.putNextEntry(newEntry);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                zos.closeEntry();
            }
        }
    }

    // ── Code-point walking utilities ─────────────────────────────────────

    static void walkProviderChars(FontProvider prov, IntConsumer consumer) {
        for (String row : prov.chars) {
            walkStringCodePoints(row, consumer);
        }
    }

    static void walkStringCodePoints(String str, IntConsumer consumer) {
        int i = 0;
        while (i < str.length()) {
            int codePoint = str.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if (codePoint > 32) {
                consumer.accept(codePoint);
            }
            i += charCount;
        }
    }
}

// ── Font JSON structures ─────────────────────────────────────────────

class FontConfig {
    List<FontProvider> providers = new ArrayList<>();
}

class FontProvider {
    String type = "bitmap";
    String file;
    int ascent = 7;
    int height = 8;
    List<String> chars = new ArrayList<>();
}
