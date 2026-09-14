package info.avicia.avoutils.features.updater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class UpdateApplierTest {

    @Test
    void isUpdateStagedInitiallyFalse() {
        UpdateApplier applier = new UpdateApplier();
        assertFalse(applier.isUpdateStaged());
    }

    @Test
    void mainDeletesCurrentJarAndInstallsPendingJar(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Path updatesDir = tempDir.resolve("updates");
        Files.createDirectories(modsDir);
        Files.createDirectories(updatesDir);

        Path currentJar = modsDir.resolve("avoutils-1.0.0.jar");
        Path staleJar = modsDir.resolve("avoutils-0.9.0.jar");
        Path unrelatedJar = modsDir.resolve("othermod.jar");
        Path finalJar = modsDir.resolve("avoutils-1.1.0.jar");
        Path pendingJar = updatesDir.resolve("avoutils-1.1.0.jar.pending");
        Path helperJar = updatesDir.resolve("avo-update-helper.jar");

        Files.writeString(currentJar, "current", StandardCharsets.UTF_8);
        Files.writeString(staleJar, "stale", StandardCharsets.UTF_8);
        Files.writeString(unrelatedJar, "keep", StandardCharsets.UTF_8);
        byte[] pendingBytes = "pending".getBytes(StandardCharsets.UTF_8);
        Files.write(pendingJar, pendingBytes);
        Files.writeString(helperJar, "helper", StandardCharsets.UTF_8);

        UpdateApplier.main(new String[] {
                modsDir.toString(),
                currentJar.toString(),
                pendingJar.toString(),
                finalJar.toString(),
                helperJar.toString()
        });

        assertTrue(Files.exists(finalJar));
        assertArrayEquals(pendingBytes, Files.readAllBytes(finalJar));
        assertFalse(Files.exists(currentJar));
        assertFalse(Files.exists(staleJar));
        assertTrue(Files.exists(unrelatedJar));
        assertFalse(Files.exists(pendingJar));
        assertFalse(Files.exists(helperJar));
    }

    @Test
    void mainReturnsImmediatelyWhenArgsMissing() {
        // Should not throw with insufficient args
        UpdateApplier.main(new String[] {"arg1", "arg2"});
    }

    @Test
    void mainReplacesCurrentJarInPlaceWhenCurrentAndFinalMatch(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Path updatesDir = tempDir.resolve("updates");
        Files.createDirectories(modsDir);
        Files.createDirectories(updatesDir);

        Path currentJar = modsDir.resolve("avoutils-1.1.0.jar");
        Path staleJar = modsDir.resolve("avoutils-1.0.0.jar");
        Path unrelatedJar = modsDir.resolve("othermod.jar");
        Path pendingJar = updatesDir.resolve("avoutils-1.1.0.jar.pending");
        Path helperJar = updatesDir.resolve("avo-update-helper.jar");

        Files.writeString(currentJar, "current", StandardCharsets.UTF_8);
        Files.writeString(staleJar, "stale", StandardCharsets.UTF_8);
        Files.writeString(unrelatedJar, "keep", StandardCharsets.UTF_8);
        byte[] pendingBytes = "pending".getBytes(StandardCharsets.UTF_8);
        Files.write(pendingJar, pendingBytes);
        Files.writeString(helperJar, "helper", StandardCharsets.UTF_8);

        UpdateApplier.main(new String[] {
                modsDir.toString(),
                currentJar.toString(),
                pendingJar.toString(),
                currentJar.toString(), // finalJar == currentJar
                helperJar.toString()
        });

        assertTrue(Files.exists(currentJar));
        assertArrayEquals(pendingBytes, Files.readAllBytes(currentJar));
        assertFalse(Files.exists(staleJar));
        assertTrue(Files.exists(unrelatedJar));
        assertFalse(Files.exists(pendingJar));
        assertFalse(Files.exists(helperJar));
    }

    @Test
    void mainWithPidArgumentAppliesSuccessfully(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Path updatesDir = tempDir.resolve("updates");
        Files.createDirectories(modsDir);
        Files.createDirectories(updatesDir);

        Path currentJar = modsDir.resolve("avoutils-1.0.0.jar");
        Path finalJar = modsDir.resolve("avoutils-1.1.0.jar");
        Path pendingJar = updatesDir.resolve("avoutils-1.1.0.jar.pending");
        Path helperJar = updatesDir.resolve("avo-update-helper.jar");

        Files.writeString(currentJar, "current", StandardCharsets.UTF_8);
        byte[] pendingBytes = "newversion".getBytes(StandardCharsets.UTF_8);
        Files.write(pendingJar, pendingBytes);
        Files.writeString(helperJar, "helper", StandardCharsets.UTF_8);

        // Pass 6th arg as an already non-existent PID (e.g. 99999999)
        UpdateApplier.main(new String[] {
                modsDir.toString(),
                currentJar.toString(),
                pendingJar.toString(),
                finalJar.toString(),
                helperJar.toString(),
                "99999999"
        });

        assertTrue(Files.exists(finalJar));
        assertArrayEquals(pendingBytes, Files.readAllBytes(finalJar));
        assertFalse(Files.exists(currentJar));
    }

    @Test
    void waitForProcessToExitHandlesInvalidOrTerminatedPid() {
        assertDoesNotThrow(() -> UpdateApplier.waitForProcessToExit(-1, 100));
        assertDoesNotThrow(() -> UpdateApplier.waitForProcessToExit(999999999, 100));
    }

    @Test
    void applyWithRetriesRetriesUntilSuccess(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Path updatesDir = tempDir.resolve("updates");
        Files.createDirectories(modsDir);
        Files.createDirectories(updatesDir);

        Path currentJar = modsDir.resolve("avoutils-1.0.0.jar");
        Path staleJar = modsDir.resolve("avoutils-0.9.0.jar");
        Path finalJar = modsDir.resolve("avoutils-1.1.0.jar");
        Path pendingJar = updatesDir.resolve("avoutils-1.1.0.jar.pending");

        Files.writeString(currentJar, "current", StandardCharsets.UTF_8);
        Files.writeString(staleJar, "stale", StandardCharsets.UTF_8);
        byte[] pendingBytes = "pending".getBytes(StandardCharsets.UTF_8);
        Files.write(pendingJar, pendingBytes);

        // Simulate flaky delete: fail first 2 attempts on the stale jar
        FlakyDeleteTracker tracker = new FlakyDeleteTracker(staleJar, 2);
        List<String> logs = new ArrayList<>();

        boolean result = UpdateApplier.applyWithRetries(
                modsDir, currentJar, pendingJar, finalJar,
                5, 0,
                Files::exists,
                deleteConsumer(tracker::deleteIfExists),
                UpdateApplier.wrapMove(),
                UpdateApplier.wrapList(),
                ignored -> {},
                logs::add);

        assertTrue(result);
        assertTrue(Files.exists(finalJar));
        assertArrayEquals(pendingBytes, Files.readAllBytes(finalJar));
        assertFalse(Files.exists(currentJar));
        assertFalse(Files.exists(staleJar));
        assertTrue(logs.stream().anyMatch(m -> m.contains("failed")));
        assertTrue(logs.stream().anyMatch(m -> m.contains("successfully")));
    }

    @Test
    void applyWithRetriesGivesUpAfterMaxAttempts(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Path updatesDir = tempDir.resolve("updates");
        Files.createDirectories(modsDir);
        Files.createDirectories(updatesDir);

        Path currentJar = modsDir.resolve("avoutils-1.0.0.jar");
        Path pendingJar = updatesDir.resolve("avoutils-1.1.0.jar.pending");
        // finalJar parent doesn't exist, so move will always fail
        Path finalJar = tempDir.resolve("missing").resolve("avoutils-1.1.0.jar");

        Files.writeString(currentJar, "current", StandardCharsets.UTF_8);
        Files.writeString(pendingJar, "pending", StandardCharsets.UTF_8);
        List<String> logs = new ArrayList<>();

        boolean result = UpdateApplier.applyWithRetries(
                modsDir, currentJar, pendingJar, finalJar,
                2, 0,
                Files::exists,
                UpdateApplier.wrapDelete(),
                UpdateApplier.wrapMove(),
                UpdateApplier.wrapList(),
                ignored -> {},
                logs::add);

        assertFalse(result);
        assertTrue(logs.stream().anyMatch(m -> m.contains("Gave up")));
    }

    // ── Test helpers ────────────────────────────────────────────────────

    private static Consumer<Path> deleteConsumer(IOConsumer<Path> delegate) {
        return path -> {
            try {
                delegate.accept(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    @FunctionalInterface
    interface IOConsumer<T> {
        void accept(T t) throws IOException;
    }

    /**
     * Simulates a file that fails to delete for the first N attempts.
     */
    static class FlakyDeleteTracker {
        private final Path flakyPath;
        private final int failuresBeforeSuccess;
        private int attempts = 0;

        FlakyDeleteTracker(Path flakyPath, int failuresBeforeSuccess) {
            this.flakyPath = flakyPath;
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        void deleteIfExists(Path path) throws IOException {
            if (path.equals(flakyPath) && attempts++ < failuresBeforeSuccess) {
                throw new IOException("Simulated lock on " + path.getFileName());
            }
            Files.deleteIfExists(path);
        }
    }
}
