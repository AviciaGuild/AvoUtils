package info.avicia.avoutils.features.updater;

import info.avicia.avoutils.AvoUtilsMod;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Handles swapping the old mod jar for the newly downloaded one.
 *
 * On Unix, jars can be replaced while loaded, so the swap happens immediately.
 *
 * On Windows, the JVM holds file locks on loaded jars, so this class copies
 * the running mod jar to a helper jar and spawns a separate Java process (via
 * a JVM shutdown hook) that waits for the parent Minecraft PID to exit and
 * completes the delete-and-move once the lock is released.
 * The helper process runs {@link #main(String[])} with the helper jar on its classpath,
 * so it does not lock the original mod jar.
 */
public final class UpdateApplier {

    private static final int DEFAULT_RETRY_ATTEMPTS = 40;
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 1500;
    private static final Consumer<String> DEFAULT_LOGGER = message -> System.err.println("[AvoUtils] [Updater] " + message);

    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    private volatile PendingInstall pendingInstall;

    // ── Standalone helper entry point (launched on Windows) ─────────────

    /**
     * Entry point for the Windows update helper process.
     * Args: modsDir currentJar pendingJar finalJar helperJar [parentPid]
     */
    public static void main(String[] args) {
        if (args.length < 5) {
            return;
        }

        Path modsDir = Path.of(args[0]);
        Path currentJar = Path.of(args[1]);
        Path pendingJar = Path.of(args[2]);
        Path finalJar = Path.of(args[3]);
        Path helperJar = Path.of(args[4]);

        if (args.length >= 6) {
            try {
                long pid = Long.parseLong(args[5]);
                waitForProcessToExit(pid, 30_000);
            } catch (NumberFormatException ignored) {
            }
        }

        applyWithRetries(modsDir, currentJar, pendingJar, finalJar,
                DEFAULT_RETRY_ATTEMPTS, DEFAULT_RETRY_DELAY_MILLIS,
                Files::exists,
                wrapDelete(),
                wrapMove(),
                wrapList(),
                UpdateApplier::sleep,
                DEFAULT_LOGGER);

        deleteQuietly(pendingJar, wrapDelete());
        deleteQuietly(helperJar, wrapDelete());
    }

    static void waitForProcessToExit(long pid, long timeoutMillis) {
        try {
            Optional<ProcessHandle> handleOpt = ProcessHandle.of(pid);
            if (handleOpt.isPresent()) {
                ProcessHandle handle = handleOpt.get();
                if (handle.isAlive()) {
                    handle.onExit().get(timeoutMillis, TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Stages the update for application. On Unix, the swap happens immediately.
     * On Windows, a shutdown hook is registered to launch a helper process.
     *
     * @param pendingJar path to the downloaded {@code .pending} jar
     * @param currentJar path to the currently running jar in {@code mods/}, or null if unknown
     * @param targetJar  desired destination path for the new jar
     */
    public void stageUpdate(Path pendingJar, Path currentJar, Path targetJar) {
        Path modsDir = targetJar.getParent();

        if (isWindows()) {
            stageWindowsUpdate(modsDir, currentJar, pendingJar, targetJar);
        } else {
            applyUnixUpdate(modsDir, currentJar, pendingJar, targetJar);
        }
    }

    public boolean isUpdateStaged() {
        return shutdownHookRegistered.get() || pendingInstall != null;
    }

    // ── Unix: immediate swap ────────────────────────────────────────────

    private void applyUnixUpdate(Path modsDir, Path currentJar, Path pendingJar, Path targetJar) {
        try {
            // On Unix, loaded jars can be unlinked while in use
            if (currentJar != null && !currentJar.equals(targetJar) && Files.exists(currentJar)) {
                Files.deleteIfExists(currentJar);
            }

            moveAtomically(pendingJar, targetJar);
            deleteStaleJars(modsDir, targetJar, wrapDelete(), wrapList());
            pendingInstall = new PendingInstall(pendingJar, targetJar, modsDir, currentJar);
            AvoUtilsMod.LOGGER.info("[AvoUtils] [Updater] Applied update: {} -> {}", pendingJar.getFileName(), targetJar.getFileName());
        } catch (IOException e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] [Updater] Failed to apply Unix update", e);
            throw new UncheckedIOException(e);
        }
    }

    // ── Windows: deferred swap via helper process ───────────────────────

    private void stageWindowsUpdate(Path modsDir, Path currentJar, Path pendingJar, Path targetJar) {
        pendingInstall = new PendingInstall(pendingJar, targetJar, modsDir, currentJar);

        if (!shutdownHookRegistered.compareAndSet(false, true)) {
            AvoUtilsMod.LOGGER.info("[AvoUtils] [Updater] Shutdown hook already registered, updated pending install");
            return;
        }

        addShutdownHook(new Thread(this::applyPendingInstallOnShutdown, "AvoUtils-UpdateApplier"));
        AvoUtilsMod.LOGGER.info("[AvoUtils] [Updater] Update staged: {} -> {}", pendingJar.getFileName(), targetJar.getFileName());
    }

    void addShutdownHook(Thread thread) {
        Runtime.getRuntime().addShutdownHook(thread);
    }

    void applyPendingInstallOnShutdown() {
        PendingInstall install = pendingInstall;
        if (install == null || !Files.exists(install.pendingJar())) {
            return;
        }

        try {
            launchWindowsHelper(install);
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] [Updater] Failed to launch Windows update helper", e);
        }
    }

    private void launchWindowsHelper(PendingInstall install) throws IOException {
        Path updatesDir = install.pendingJar().getParent();
        if (updatesDir == null) {
            throw new IOException("Missing updates directory for pending install.");
        }

        // Copy the running mod jar as the helper, so the helper process
        // doesn't lock the original jar
        Path sourceJar = resolveHelperSourceJar(install.currentJar());
        if (sourceJar == null) {
            sourceJar = install.pendingJar(); // pending jar contains UpdateApplier.class
        }
        Path helperJar = updatesDir.resolve("avo-update-helper.jar");
        Files.copy(sourceJar, helperJar, StandardCopyOption.REPLACE_EXISTING);

        Path currentJar = install.currentJar() != null ? install.currentJar() : install.finalJar();

        launchHelperProcess(
                resolveJavaExecutable(),
                helperJar,
                install.modsDir(),
                currentJar,
                install.pendingJar(),
                install.finalJar(),
                ProcessHandle.current().pid());

        AvoUtilsMod.LOGGER.info("[AvoUtils] [Updater] Launched Windows update helper");
    }

    void launchHelperProcess(
            String javaExe, Path helperJar, Path modsDir,
            Path currentJar, Path pendingJar, Path finalJar,
            long parentPid) throws IOException {
        new ProcessBuilder(
                javaExe,
                "-cp",
                helperJar.toString(),
                UpdateApplier.class.getName(),
                modsDir.toString(),
                currentJar.toString(),
                pendingJar.toString(),
                finalJar.toString(),
                helperJar.toString(),
                String.valueOf(parentPid))
                .start();
    }

    // ── Retry-based install logic (used by helper process and tests) ────

    static boolean applyWithRetries(
            Path modsDir, Path currentJar, Path pendingJar, Path finalJar,
            int retryAttempts, long retryDelayMillis,
            Predicate<Path> exists, Consumer<Path> deleteIfExists,
            BiConsumer<Path, Path> move, Function<Path, List<Path>> list,
            LongConsumer sleeper, Consumer<String> logger) {

        Exception lastFailure = null;

        for (int attempt = 0; attempt < retryAttempts; attempt++) {
            try {
                applyInstallAttempt(modsDir, currentJar, pendingJar, finalJar,
                        exists, deleteIfExists, move, list);
                logger.accept("Update installed successfully on attempt " + (attempt + 1));
                return true;
            } catch (Exception e) {
                lastFailure = e;
                logger.accept("Attempt " + (attempt + 1) + "/" + retryAttempts
                        + " failed: " + failureMessage(e));
                if (attempt + 1 < retryAttempts) {
                    sleeper.accept(retryDelayMillis);
                }
            }
        }

        if (lastFailure != null) {
            logger.accept("Gave up after " + retryAttempts + " attempts: " + failureMessage(lastFailure));
        }
        return false;
    }

    private static void applyInstallAttempt(
            Path modsDir, Path currentJar, Path pendingJar, Path finalJar,
            Predicate<Path> exists, Consumer<Path> deleteIfExists,
            BiConsumer<Path, Path> move, Function<Path, List<Path>> list) throws IOException {

        // Delete the old jar if it's different from the target
        if (!currentJar.equals(finalJar) && exists.test(currentJar)) {
            deletePath(currentJar, deleteIfExists);
        }

        // Move pending jar into place
        if (exists.test(pendingJar)) {
            movePath(pendingJar, finalJar, move);
        } else if (!exists.test(finalJar)) {
            throw new IOException("Pending jar missing and final jar not in place.");
        }

        // Clean up any stale avoutils jars
        deleteStaleJars(modsDir, finalJar, deleteIfExists, list);
    }

    private static void deleteStaleJars(
            Path modsDir, Path finalJar,
            Consumer<Path> deleteIfExists, Function<Path, List<Path>> list) throws IOException {
        List<Path> stale = listStaleJars(modsDir, finalJar, list);
        for (Path path : stale) {
            deletePath(path, deleteIfExists);
        }

        List<Path> remaining = listStaleJars(modsDir, finalJar, list);
        if (!remaining.isEmpty()) {
            throw new IOException("Failed to delete stale jars: " + remaining);
        }
    }

    private static List<Path> listStaleJars(
            Path modsDir, Path finalJar, Function<Path, List<Path>> list) throws IOException {
        try {
            String finalJarName = finalJar.getFileName().toString();
            return list.apply(modsDir).stream()
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.contains("avoutils") && name.endsWith(".jar");
                    })
                    .filter(p -> !p.getFileName().toString().equalsIgnoreCase(finalJarName))
                    .toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    // ── Utility methods ─────────────────────────────────────────────────


    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deletePath(Path path, Consumer<Path> deleteIfExists) throws IOException {
        try {
            deleteIfExists.accept(path);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void movePath(Path source, Path destination, BiConsumer<Path, Path> move) throws IOException {
        try {
            move.accept(source, destination);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void deleteQuietly(Path path, Consumer<Path> deleteIfExists) {
        try {
            deletePath(path, deleteIfExists);
        } catch (Exception ignored) {
        }
    }

    private static String failureMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private Path resolveHelperSourceJar(Path installedJar) {
        try {
            return resolveCodeSourceJar();
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.warn("[AvoUtils] [Updater] Falling back to installed jar for helper source", e);
            return installedJar;
        }
    }

    Path resolveCodeSourceJar() {
        try {
            java.net.URI location = UpdateApplier.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();
            Path path = Path.of(location);
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Not a packaged jar (development environment).");
            }
            return path;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to resolve jar location.", e);
        }
    }

    String resolveJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            // Prefer javaw.exe on Windows to avoid a visible console window
            Path javawExe = Path.of(javaHome, "bin", "javaw.exe");
            if (Files.exists(javawExe)) {
                return javawExe.toString();
            }
            Path javaExe = Path.of(javaHome, "bin", "java.exe");
            if (Files.exists(javaExe)) {
                return javaExe.toString();
            }
            Path javaBin = Path.of(javaHome, "bin", "java");
            if (Files.exists(javaBin)) {
                return javaBin.toString();
            }
        }
        return "java";
    }

    boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    static Consumer<Path> wrapDelete() {
        return path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    static BiConsumer<Path, Path> wrapMove() {
        return (source, destination) -> {
            try {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    static Function<Path, List<Path>> wrapList() {
        return directory -> {
            try (Stream<Path> stream = Files.list(directory)) {
                return stream.toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    record PendingInstall(Path pendingJar, Path finalJar, Path modsDir, Path currentJar) {}
}
