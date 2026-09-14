package info.avicia.avoutils.features.updater;

/**
 * Holds the result of a Modrinth update check.
 */
public record UpdateCheckResult(
        boolean updateAvailable,
        String currentVersion,
        String latestVersion,
        String downloadUrl,
        String sha512Hash,
        String fileName,
        long fileSize,
        String changelog
) {
    /**
     * Creates a result indicating no update is available.
     */
    public static UpdateCheckResult upToDate(String currentVersion) {
        return new UpdateCheckResult(false, currentVersion, currentVersion, null, null, null, 0, null);
    }
}
