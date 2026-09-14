package info.avicia.avoutils.features.updater;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.ClientVersion;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Queries the Modrinth API to check for newer versions of AvoUtils and
 * downloads update files with SHA-512 integrity verification.
 */
public class ModrinthUpdateChecker {

    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private static final String PROJECT_SLUG = "avoutils";
    private static final String USER_AGENT = "AviciaGuild/AvoUtils/%s (https://github.com/AviciaGuild/AvoUtils)";

    static final String STAGING_DIR_NAME = ".avoutils-update";

    private final HttpClient httpClient;
    private final String userAgent;

    public ModrinthUpdateChecker() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    ModrinthUpdateChecker(HttpClient httpClient) {
        this.httpClient = httpClient;
        String version = ClientVersion.resolveInstalledVersion();
        this.userAgent = String.format(USER_AGENT, version);
    }

    // ── Update check ────────────────────────────────────────────────────

    /**
     * Queries Modrinth for the latest release version compatible with the
     * current Minecraft version and Fabric loader.
     */
    public CompletableFuture<UpdateCheckResult> checkForUpdate() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            String current = ClientVersion.resolveInstalledVersion();
            AvoUtilsMod.LOGGER.debug("[AvoUtils] [Updater] Skipping update check in dev environment");
            return CompletableFuture.completedFuture(UpdateCheckResult.upToDate(current));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doCheck();
            } catch (Exception e) {
                throw new RuntimeException("Update check failed", e);
            }
        });
    }

    private UpdateCheckResult doCheck() throws IOException, InterruptedException {
        String currentVersion = ClientVersion.resolveInstalledVersion();
        String mcVersion = resolveMinecraftVersion();

        String loaders = URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8);
        String gameVersions = URLEncoder.encode("[\"" + mcVersion + "\"]", StandardCharsets.UTF_8);

        String url = MODRINTH_API + "/project/" + PROJECT_SLUG + "/version"
                + "?loaders=" + loaders
                + "&game_versions=" + gameVersions;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Modrinth API returned HTTP " + response.statusCode());
        }

        return parseVersionResponse(response.body(), currentVersion);
    }

    /**
     * Parses the Modrinth version list response and finds the latest stable
     * release that is newer than the current version.
     */
    UpdateCheckResult parseVersionResponse(String json, String currentVersion) {
        JsonArray versions = JsonParser.parseString(json).getAsJsonArray();

        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();

            String versionType = version.get("version_type").getAsString();
            if (!"release".equals(versionType)) {
                continue;
            }

            String versionNumber = version.get("version_number").getAsString();

            if (!isNewer(versionNumber, currentVersion)) {
                // Versions are ordered newest-first; if this one isn't newer, none after will be
                return UpdateCheckResult.upToDate(currentVersion);
            }

            // Find the primary file
            JsonArray files = version.getAsJsonArray("files");
            JsonObject primaryFile = findPrimaryFile(files);
            if (primaryFile == null) {
                continue;
            }

            String downloadUrl = primaryFile.get("url").getAsString();
            String fileName = primaryFile.get("filename").getAsString();
            long fileSize = primaryFile.get("size").getAsLong();

            JsonObject hashes = primaryFile.has("hashes") && primaryFile.get("hashes").isJsonObject()
                    ? primaryFile.getAsJsonObject("hashes")
                    : null;
            String sha512 = (hashes != null && hashes.has("sha512") && !hashes.get("sha512").isJsonNull())
                    ? hashes.get("sha512").getAsString()
                    : null;

            String changelog = version.has("changelog") && !version.get("changelog").isJsonNull()
                    ? version.get("changelog").getAsString()
                    : null;

            return new UpdateCheckResult(true, currentVersion, versionNumber,
                    downloadUrl, sha512, fileName, fileSize, changelog);
        }

        return UpdateCheckResult.upToDate(currentVersion);
    }

    private JsonObject findPrimaryFile(JsonArray files) {
        if (files == null || files.isEmpty()) return null;

        // Prefer the primary file
        for (JsonElement fe : files) {
            JsonObject file = fe.getAsJsonObject();
            if (file.has("primary") && file.get("primary").getAsBoolean()) {
                return file;
            }
        }
        // Fallback to first file
        return files.get(0).getAsJsonObject();
    }

    /**
     * Compares two version strings using Fabric's semantic version parser.
     * Returns true if {@code candidate} is strictly newer than {@code current}.
     */
    static boolean isNewer(String candidate, String current) {
        try {
            SemanticVersion candidateVer = SemanticVersion.parse(candidate);
            SemanticVersion currentVer = SemanticVersion.parse(current);
            return candidateVer.compareTo(currentVer) > 0;
        } catch (VersionParsingException e) {
            AvoUtilsMod.LOGGER.warn("[AvoUtils] [Updater] Failed to parse versions: {} vs {}", candidate, current, e);
            return false;
        }
    }

    // ── Download ────────────────────────────────────────────────────────

    /**
     * Downloads the update jar to a staging directory inside the mods folder.
     * Verifies the SHA-512 hash after download.
     *
     * @return the path to the verified downloaded jar
     */
    public CompletableFuture<Path> downloadUpdate(UpdateCheckResult result) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doDownload(result);
            } catch (Exception e) {
                throw new RuntimeException("Update download failed", e);
            }
        });
    }

    private Path doDownload(UpdateCheckResult result) throws IOException, InterruptedException, NoSuchAlgorithmException {
        Path modsDir = resolveModsDir();
        Path stagingDir = modsDir.resolve(STAGING_DIR_NAME);
        Files.createDirectories(stagingDir);

        String safeFileName = Path.of(result.fileName()).getFileName().toString();
        Path pendingFile = stagingDir.resolve(safeFileName + ".pending");
        Path tempFile = stagingDir.resolve(safeFileName + ".tmp");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(result.downloadUrl()))
                .header("User-Agent", userAgent)
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Download failed: HTTP " + response.statusCode());
        }

        // Stream download while computing SHA-512
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        try (InputStream in = response.body();
             OutputStream fileOut = Files.newOutputStream(tempFile);
             DigestOutputStream digestOut = new DigestOutputStream(fileOut, digest)) {
            in.transferTo(digestOut);
        }

        // Verify hash
        if (result.sha512Hash() != null) {
            String actualHash = HexFormat.of().formatHex(digest.digest());
            if (!actualHash.equalsIgnoreCase(result.sha512Hash())) {
                Files.deleteIfExists(tempFile);
                throw new IOException("SHA-512 hash mismatch! Expected: " + result.sha512Hash()
                        + ", got: " + actualHash);
            }
            AvoUtilsMod.LOGGER.info("[AvoUtils] [Updater] Download hash verified: {}", actualHash);
        }

        // Rename temp to pending
        Files.move(tempFile, pendingFile, StandardCopyOption.REPLACE_EXISTING);

        AvoUtilsMod.LOGGER.info("[AvoUtils] [Updater] Downloaded update to {}", pendingFile);
        return pendingFile;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    static String resolveMinecraftVersion() {
        return FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    /**
     * Resolves the path to the currently loaded mod jar file via Fabric Loader.
     */
    public static Path resolveCurrentJarPath() {
        try {
            return FabricLoader.getInstance()
                    .getModContainer(AvoUtilsMod.MOD_ID)
                    .flatMap(c -> c.getOrigin().getPaths().stream().findFirst())
                    .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".jar"))
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Path resolveModsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("mods");
    }

    private static final Pattern VERSION_FILE_PATTERN = Pattern.compile("(?i)^(?:avoutils|AvoUtils)[-_](.+)\\.jar$");

    public record PendingUpdate(Path pendingJar, Path targetJar, String version) {
    }

    /**
     * Finds any unapplied pending update jar in the staging directory whose
     * version is strictly newer than the currently installed version.
     */
    public static Optional<PendingUpdate> findPendingUpdate() {
        return findPendingUpdate(resolveModsDir(), ClientVersion.resolveInstalledVersion());
    }

    public static Optional<PendingUpdate> findPendingUpdate(Path modsDir, String currentVersion) {
        Path stagingDir = modsDir.resolve(STAGING_DIR_NAME);
        if (!Files.isDirectory(stagingDir)) {
            return Optional.empty();
        }

        try (Stream<Path> stream = Files.list(stagingDir)) {
            List<Path> pendingFiles = stream
                    .filter(p -> p.getFileName().toString().endsWith(".pending"))
                    .toList();

            PendingUpdate bestUpdate = null;

            for (Path pendingFile : pendingFiles) {
                String name = pendingFile.getFileName().toString();
                String targetName = name.substring(0, name.length() - ".pending".length());
                if (!targetName.endsWith(".jar")) {
                    continue;
                }

                String version = extractVersionFromJar(pendingFile);
                if (version == null) {
                    version = extractVersionFromFileName(targetName);
                }

                if (version != null && isNewer(version, currentVersion)) {
                    if (bestUpdate == null || isNewer(version, bestUpdate.version())) {
                        bestUpdate = new PendingUpdate(pendingFile, modsDir.resolve(targetName), version);
                    }
                }
            }

            return Optional.ofNullable(bestUpdate);
        } catch (IOException e) {
            AvoUtilsMod.LOGGER.warn("[AvoUtils] [Updater] Failed to scan staging directory for pending updates", e);
            return Optional.empty();
        }
    }

    static String extractVersionFromJar(Path jarPath) {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry != null) {
                try (InputStream in = zip.getInputStream(entry);
                     InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("version")) {
                        return json.get("version").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.debug("[AvoUtils] [Updater] Could not read fabric.mod.json from {}: {}",
                    jarPath.getFileName(), e.getMessage());
        }
        return null;
    }

    static String extractVersionFromFileName(String targetFileName) {
        Matcher matcher = VERSION_FILE_PATTERN.matcher(targetFileName);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Cleans up leftover temporary files from aborted downloads, keeping
     * an active pending file if specified.
     */
    public static void cleanupStagingDir() {
        cleanupStagingDir(resolveModsDir(), null);
    }

    public static void cleanupStagingDir(Path keepPendingFile) {
        cleanupStagingDir(resolveModsDir(), keepPendingFile);
    }

    public static void cleanupStagingDir(Path modsDir, Path keepPendingFile) {
        try {
            Path stagingDir = modsDir.resolve(STAGING_DIR_NAME);
            if (!Files.isDirectory(stagingDir)) return;

            try (Stream<Path> entries = Files.list(stagingDir)) {
                entries.filter(p -> {
                    if (keepPendingFile != null && p.equals(keepPendingFile)) {
                        return false;
                    }
                    String name = p.getFileName().toString();
                    return name.endsWith(".tmp") || name.endsWith(".pending") || name.endsWith(".jar");
                }).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                        AvoUtilsMod.LOGGER.debug("[AvoUtils] [Updater] Cleaned up leftover file: {}", p.getFileName());
                    } catch (IOException ignored) {
                    }
                });
            }

            try (Stream<Path> entries = Files.list(stagingDir)) {
                if (entries.findAny().isEmpty()) {
                    Files.deleteIfExists(stagingDir);
                }
            }
        } catch (IOException e) {
            AvoUtilsMod.LOGGER.warn("[AvoUtils] [Updater] Failed to clean staging directory", e);
        }
    }
}
