package info.avicia.avoutils.features.emojis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwemojiManagerSanitizeTest {

    @TempDir
    Path tempDir;

    @Test
    void sanitizeRenamesFontJsonAndRewritesPackMeta() throws Exception {
        Path source = tempDir.resolve("source.zip");
        Path destination = tempDir.resolve("dest.zip");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(source))) {
            zos.putNextEntry(new ZipEntry("assets/minecraft/font/default.json"));
            zos.write("{\"providers\":[]}".getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write("{\"pack\":{\"pack_format\":18,\"description\":\"x\"}}".getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("keep/me.txt"));
            zos.write("hello".getBytes());
            zos.closeEntry();
        }

        TwemojiManager manager = new TwemojiManager(tempDir.resolve("game"), 75);
        invokeSanitize(manager, source, destination);

        try (ZipFile zip = new ZipFile(destination.toFile())) {
            assertNull(zip.getEntry("assets/minecraft/font/default.json"));
            assertNotNull(zip.getEntry(TwemojiManager.RAW_JSON_PATH));
            assertNotNull(zip.getEntry("keep/me.txt"));

            String mcmeta = new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes());
            assertTrue(mcmeta.contains("\"pack_format\":75"));
        }
    }

    private static void invokeSanitize(TwemojiManager manager, Path source, Path destination) throws Exception {
        Method method = TwemojiManager.class.getDeclaredMethod("sanitize", Path.class, Path.class);
        method.setAccessible(true);
        method.invoke(manager, source, destination);
    }
}
