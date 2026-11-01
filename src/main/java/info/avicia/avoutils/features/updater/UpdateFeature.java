package info.avicia.avoutils.features.updater;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.command.AvoCommands;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.util.ClientVersion;
import info.avicia.avoutils.core.util.WynnPillUtil;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

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

        // Auto-check on world join (once per session on Wynncraft)
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!WynncraftServerPolicy.isNetworkingAllowed()) return;
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

    @Override
    public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        // /avo update → check and show status; check → force re-check; download → download and stage;
        // restart → restart Minecraft to apply the staged update
        root.then(literal("update")
                .executes(context -> ifNetworkingAllowed(context.getSource(), () -> handleCheckCommand(false)))
                .then(literal("check")
                        .executes(context -> ifNetworkingAllowed(context.getSource(), () -> handleCheckCommand(true))))
                .then(literal("download")
                        .executes(context -> ifNetworkingAllowed(context.getSource(), this::handleDownloadCommand)))
                .then(literal("restart")
                        .executes(context -> ifNetworkingAllowed(context.getSource(), this::handleRestartCommand))));
    }

    private static int ifNetworkingAllowed(FabricClientCommandSource source, Runnable action) {
        if (AvoCommands.ensureNetworkingAllowed(source)) {
            action.run();
        }
        return 1;
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
                sendChatMessage("Download already in progress...", ChatFormatting.GRAY);
            }
            return CompletableFuture.completedFuture(null);
        }
        MutableComponent downloadingMsg = WynnPillUtil.createPrefixedPill("AvoUtils", false)
                .append(Component.literal("Downloading v" + result.latestVersion() + "...").withStyle(ChatFormatting.GRAY));
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
            sendChatMessage("Checking for updates...", ChatFormatting.GRAY);
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
                sendChatMessage("No update available.", ChatFormatting.GRAY);
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

        sendChatMessage("Restarting Minecraft to apply update...", ChatFormatting.GRAY);
        stopClient();
    }

    void stopClient() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(client::destroy);
        }
    }

    // ── Chat notifications ──────────────────────────────────────────────

    /**
     * Sends the post-download announcement with an interactive RESTART pill.
     */
    public static void sendUpdateDownloadedMessage(String version) {
        sendChatMessage(buildUpdateDownloadedMessage(version));
    }

    static MutableComponent buildUpdateDownloadedMessage(String version) {
        return WynnPillUtil.createPrefixedPill("AvoUtils", false)
                .append(Component.literal("Update ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("v" + version).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" downloaded! ").withStyle(ChatFormatting.GRAY))
                .append(WynnPillUtil.createClickable("RESTART", ChatFormatting.GREEN, ChatFormatting.BLACK,
                        "/avo update restart", "Click to restart Minecraft now"));
    }

    /**
     * Sends the pending-update-recovered announcement with an interactive RESTART pill.
     */
    public static void sendPendingReadyMessage(String version) {
        sendChatMessage(buildPendingReadyMessage(version));
    }

    static MutableComponent buildPendingReadyMessage(String version) {
        return WynnPillUtil.createPrefixedPill("AvoUtils", false)
                .append(Component.literal("A previously downloaded update (").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("v" + version).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(") is ready to install! ").withStyle(ChatFormatting.GRAY))
                .append(WynnPillUtil.createClickable("RESTART", ChatFormatting.GREEN, ChatFormatting.BLACK,
                        "/avo update restart", "Click to restart Minecraft now"));
    }

    /**
     * Sends an update announcement with an interactive UPDATE pill to the player's chat.
     */
    public static void sendUpdateAvailableMessage(UpdateCheckResult result) {
        sendChatMessage(buildUpdateAvailableMessage(result));
    }

    static MutableComponent buildUpdateAvailableMessage(UpdateCheckResult result) {
        return WynnPillUtil.createPrefixedPill("AvoUtils", false)
                .append(Component.literal("Update available: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("v" + result.currentVersion()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("v" + result.latestVersion()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                .append(WynnPillUtil.createClickable("UPDATE", ChatFormatting.GREEN, ChatFormatting.BLACK,
                        "/avo update download", "Click to update"));
    }

    static MutableComponent buildUpToDateMessage(String currentVersion) {
        return WynnPillUtil.createPrefixedPill("AvoUtils", false)
                .append(Component.literal("You are running the latest version (").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("v" + currentVersion).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(").").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Sends a rich {@link Component} message to the player's chat on the client thread.
     */
    public static void sendChatMessage(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        client.execute(() -> {
            if (client.player != null) {
                client.player.displayClientMessage(message, false);
            }
        });
    }

    /**
     * Sends a formatted update-related message to the player's chat.
     */
    public static void sendChatMessage(String text, ChatFormatting color) {
        sendFeedbackMessage(text, color, false);
    }

    public static void sendErrorMessage(String text) {
        sendFeedbackMessage(text, ChatFormatting.RED, true);
    }

    private static void sendFeedbackMessage(String text, ChatFormatting color, boolean isError) {
        MutableComponent message = WynnPillUtil.createPrefixedPill("AvoUtils", isError)
                .append(Component.literal(text).withStyle(color));
        sendChatMessage(message);
    }
}
