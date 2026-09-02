package info.avicia.avoutils.features.emojis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwemojiManagerLoadTest {

    @TempDir
    Path tempDir;

    @Test
    void loadResourcesParsesPuaMappingsAndShortcodes() throws Exception {
        Path zipPath = tempDir.resolve("avoutils/emojis/avoutils-twemoji.zip");
        Files.createDirectories(zipPath.getParent());

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry(TwemojiManager.RAW_JSON_PATH));
            zos.write(("{\"providers\":[{\"type\":\"bitmap\",\"file\":\"a\",\"ascent\":7,\"height\":8,"
                    + "\"chars\":[\"\uD83D\uDE00\"]}]}").getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(TwemojiManager.SHORTCODES_JSON_PATH));
            zos.write("{\"grinning\":\"\uD83D\uDE00\"}".getBytes());
            zos.closeEntry();
        }

        TwemojiManager manager = new TwemojiManager(tempDir, 75);
        manager.loadResources();

        assertFalse(manager.standardCharToPua.isEmpty());
        assertTrue(manager.standardEmojis.containsKey("grinning"));
        assertNotNull(manager.standardFontConfig);
    }
}
