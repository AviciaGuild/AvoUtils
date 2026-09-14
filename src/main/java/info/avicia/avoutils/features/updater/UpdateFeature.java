package info.avicia.avoutils.features.updater;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.util.ClientVersion;
import info.avicia.avoutils.core.util.WynnPillUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinates the update lifecycle: check → notify → download → stage.
 *
 * Checks for updates once per game session when the player joins a world
 * (if reminders are enabled). Exposes state for the config screen and
 * {@code /avo update} command.
 */
public class UpdateFeature implements AvoFeature {

    public enum UpdateState {
        UNCHECKED,
        CHECKING,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        DOWNLOADING,
        READY_TO_RESTART,
        ERROR
    }

    private final ModrinthUpdateChecker checker;
    private final UpdateApplier applier;

    private final AtomicReference<UpdateState> state = new AtomicReference<>(UpdateState.UNCHECKED);
    private final AtomicReference<UpdateCheckResult> lastResult = new AtomicReference<>();
    private final AtomicReference<String> errorMessage = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<UpdateCheckResult>> inFlightCheck = new AtomicReference<>();
    private final AtomicBoolean sessionChecked = new AtomicBoolean(false);

    private ModConfig config;

    public UpdateFeature() {
        this(new ModrinthUpdateChecker(), new UpdateApplier());
    }

    UpdateFeature(ModrinthUpdateChecker checker, UpdateApplier applier) {
        this.checker = checker;
        this.applier = applier;
    }

    @Override
    public void initialize(ModConfig config) {
        this.config = config;

        // Recover unapplied pending update from a previous session (e.g. abrupt exit)
        recoverPendingUpdateIfExists();

        // Auto-check on world join (once per session)
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!sessionChecked.compareAndSet(false, true)) return;

            if (state.get() == UpdateState.READY_TO_RESTART) {
                if (config.updateRemindersEnabled) {
                    UpdateCheckResult res = lastResult.get();
                    String ver = res != null ? res.latestVersion() : "";
                    sendPendingReadyMessage(ver);
                }
                return;
            }

            checkForUpdate().thenAccept(result -> {
                if (result != null && result.updateAvailable() && config.updateRemindersEnabled) {
                    sendUpdateAvailableMessage(result);
                }
            });
        });

        AvoUtilsMod.LOGGER.info("[AvoUtils] [Updater] Initialized");
    }

    void recoverPendingUpdateIfExists() {
        Optional<ModrinthUpdateChecker.PendingUpdate> pendingOpt = ModrinthUpdateChecker.findPendingUpdate();
        if (pendingOpt.isPresent()) {
            ModrinthUpdateChecker.PendingUpdate pending = pendingOpt.get();
            Path currentJar = ModrinthUpdateChecker.resolveCurrentJarPath();
            applier.stageUpdate(pending.pendingJar(), currentJar, pending.targetJar());

            String currentVer = ClientVersion.resolveInstalledVersion();
            UpdateCheckResult recoveredResult = new UpdateCheckResult(
                    true, currentVer, pending.version(),
                    "", null, pending.targetJar().getFileName().toString(),
                    0, ""
            );
            lastResult.set(recoveredResult);
            state.set(UpdateState.READY_TO_RESTART);

            ModrinthUpdateChecker.cleanupStagingDir(pending.pendingJar());
            AvoUtilsMod.LOGGER.info("[AvoUtils] [Updater] Recovered pending update v{} from previous session", pending.version());
        } else {
            ModrinthUpdateChecker.cleanupStagingDir();
        }
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Checks for an update. Returns the cached result if already checked,
     * unless {@code force} is true.
     */
    public CompletableFuture<UpdateCheckResult> checkForUpdate() {
        return checkForUpdate(false);
    }

    public CompletableFuture<UpdateCheckResult> checkForUpdate(boolean force) {
        if (!force && lastResult.get() != null) {
            return CompletableFuture.completedFuture(lastResult.get());
        }

        if (!force) {
            CompletableFuture<UpdateCheckResult> running = inFlightCheck.get();
            if (running != null && !running.isDone()) {
                return running;
            }
        }

        state.set(UpdateState.CHECKING);
        CompletableFuture<UpdateCheckResult> future = checker.checkForUpdate().thenApply(result -> {
            lastResult.set(result);
            state.set(result.updateAvailable() ? UpdateState.UPDATE_AVAILABLE : UpdateState.UP_TO_DATE);
            return result;
        }).exceptionally(ex -> {
            state.set(UpdateState.ERROR);
            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            errorMessage.set(msg);
            AvoUtilsMod.LOGGER.warn("[AvoUtils] [Updater] Check failed: {}", msg);
            return null;
        });

        inFlightCheck.set(future);
        future.whenComplete((res, ex) -> inFlightCheck.compareAndSet(future, null));
        return future;
    }

    /**
     * Downloads the latest update and applies it.
     * On Unix the jar is swapped immediately; on Windows, it is staged for
     * installation on game exit via a helper process.
     */
    public CompletableFuture<Void> downloadAndApplyUpdate() {
        UpdateCheckResult result = lastResult.get();
        if (result == null || !result.updateAvailable()) {
            sendErrorMessage("No update available.");
            return CompletableFuture.failedFuture(new IllegalStateException("No update available"));
        }

        if (applier.isUpdateStaged() || state.get() == UpdateState.READY_TO_RESTART) {
            String ver = result.latestVersion();
            sendPendingReadyMessage(ver);
            return CompletableFuture.completedFuture(null);
        }

        if (!state.compareAndSet(UpdateState.UPDATE_AVAILABLE, UpdateState.DOWNLOADING)) {
            if (state.get() == UpdateState.DOWNLOADING) {
                sendChatMessage("Download already in progress...", Formatting.GRAY);
            }
            return CompletableFuture.completedFuture(null);
        }
        MutableText downloadingMsg = WynnPillUtil.createPrefixedPill("AvoUtils", false)
                .append(Text.literal("Downloading v" + result.latestVersion() + "...").formatted(Formatting.GRAY));
        sendChatMessage(downloadingMsg);

        return checker.downloadUpdate(result).thenAccept(pendingJar -> {
            Path currentJar = ModrinthUpdateChecker.resolveCurrentJarPath();
            Path targetJar = ModrinthUpdateChecker.resolveModsDir().resolve(result.fileName());

            applier.stageUpdate(pendingJar, currentJar, targetJar);
            state.set(UpdateState.READY_TO_RESTART);
            sendUpdateDownloadedMessage(result.latestVersion());
        }).exceptionally(ex -> {
            state.set(UpdateState.ERROR);
            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            errorMessage.set(msg);
            AvoUtilsMod.LOGGER.error("[AvoUtils] [Updater] Download failed: {}", msg);
            sendErrorMessage("Update failed: " + msg);
            return null;
        });
    }

    public UpdateState getState() {
        return state.get();
    }

    public UpdateCheckResult getLastCheckResult() {
        return lastResult.get();
    }

    public String getErrorMessage() {
        return errorMessage.get();
    }

    // ── Command Handlers ────────────────────────────────────────────────

    /**
     * Handles the {@code /avo update} or {@code /avo update check} command workflow.
     *
     * @param force whether to bypass cached results and re-query Modrinth
     */
    public void handleCheckCommand(boolean force) {
        if (!force && state.get() == UpdateState.READY_TO_RESTART) {
            UpdateCheckResult res = lastResult.get();
            String ver = res != null ? res.latestVersion() : "";
            sendPendingReadyMessage(ver);
            return;
        }

        if (force) {
            sendChatMessage("Checking for updates...", Formatting.GRAY);
        }
        checkForUpdate(force).thenAccept(result -> {
            if (result == null) {
                sendErrorMessage("Failed to check for updates.");
            } else if (result.updateAvailable()) {
                sendUpdateAvailableMessage(result);
            } else {
                sendChatMessage(buildUpToDateMessage(result.currentVersion()));
            }
        });
    }

    /**
     * Handles the {@code /avo update download} command workflow.
     */
    public void handleDownloadCommand() {
        if (state.get() == UpdateState.READY_TO_RESTART || applier.isUpdateStaged()) {
            UpdateCheckResult res = lastResult.get();
            String ver = res != null ? res.latestVersion() : "";
            sendPendingReadyMessage(ver);
            return;
        }

        checkForUpdate().thenAccept(result -> {
            if (result == null || !result.updateAvailable()) {
                sendChatMessage("No update available.", Formatting.GRAY);
                return;
            }
            downloadAndApplyUpdate();
        });
    }

    /**
     * Handles the {@code /avo update restart} command workflow.
     */
    public void handleRestartCommand() {
        requestRestart();
    }

    public void requestRestart() {
        if (!applier.isUpdateStaged() && state.get() != UpdateState.READY_TO_RESTART) {
            sendErrorMessage("No update is ready to be applied.");
            return;
        }

        sendChatMessage("Restarting Minecraft to apply update...", Formatting.GRAY);
        stopClient();
    }

    void stopClient() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(client::stop);
        }
    }

    // ── Chat notifications ──────────────────────────────────────────────

    /**
     * Sends the post-download announcement with an interactive RESTART pill.
     */
    public static void sendUpdateDownloadedMessage(String version) {
        sendChatMessage(buildUpdateDownloadedMessage(version));
    }

    static MutableText buildUpdateDownloadedMessage(String version) {
        return WynnPillUtil.createPrefixedPill("AvoUtils", false)
                .append(Text.literal("Update ").formatted(Formatting.GRAY))
                .append(Text.literal("v" + version).formatted(Formatting.WHITE))
                .append(Text.literal(" downloaded! ").formatted(Formatting.GRAY))
                .append(WynnPillUtil.createClickable("RESTART", Formatting.GREEN, Formatting.BLACK,
                        "/avo update restart", "Click to restart Minecraft now"));
    }

    /**
     * Sends the pending-update-recovered announcement with an interactive RESTART pill.
     */
    public static void sendPendingReadyMessage(String version) {
        sendChatMessage(buildPendingReadyMessage(version));
    }

    static MutableText buildPendingReadyMessage(String version) {
        return WynnPillUtil.createPrefixedPill("AvoUtils", false)
                .append(Text.literal("A previously downloaded update (").formatted(Formatting.GRAY))
                .append(Text.literal("v" + version).formatted(Formatting.WHITE))
                .append(Text.literal(") is ready to install! ").formatted(Formatting.GRAY))
                .append(WynnPillUtil.createClickable("RESTART", Formatting.GREEN, Formatting.BLACK,
                        "/avo update restart", "Click to restart Minecraft now"));
    }

    /**
     * Sends an update announcement with an interactive UPDATE pill to the player's chat.
     */
    public static void sendUpdateAvailableMessage(UpdateCheckResult result) {
        sendChatMessage(buildUpdateAvailableMessage(result));
    }

    static MutableText buildUpdateAvailableMessage(UpdateCheckResult result) {
        return WynnPillUtil.createPrefixedPill("AvoUtils", false)
                .append(Text.literal("Update available: ").formatted(Formatting.GRAY))
                .append(Text.literal("v" + result.currentVersion()).formatted(Formatting.WHITE))
                .append(Text.literal(" → ").formatted(Formatting.GRAY))
                .append(Text.literal("v" + result.latestVersion()).formatted(Formatting.WHITE))
                .append(Text.literal(" ").formatted(Formatting.GRAY))
                .append(WynnPillUtil.createClickable("UPDATE", Formatting.GREEN, Formatting.BLACK,
                        "/avo update download", "Click to update"));
    }

    static MutableText buildUpToDateMessage(String currentVersion) {
        return WynnPillUtil.createPrefixedPill("AvoUtils", false)
                .append(Text.literal("You are running the latest version (").formatted(Formatting.GRAY))
                .append(Text.literal("v" + currentVersion).formatted(Formatting.GREEN))
                .append(Text.literal(").").formatted(Formatting.GRAY));
    }

    /**
     * Sends a rich {@link Text} message to the player's chat on the client thread.
     */
    public static void sendChatMessage(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(message, false);
            }
        });
    }

    /**
     * Sends a formatted update-related message to the player's chat.
     */
    public static void sendChatMessage(String text, Formatting color) {
        sendFeedbackMessage(text, color, false);
    }

    public static void sendErrorMessage(String text) {
        sendFeedbackMessage(text, Formatting.RED, true);
    }

    private static void sendFeedbackMessage(String text, Formatting color, boolean isError) {
        MutableText message = WynnPillUtil.createPrefixedPill("AvoUtils", isError)
                .append(Text.literal(text).formatted(color));
        sendChatMessage(message);
    }
}
