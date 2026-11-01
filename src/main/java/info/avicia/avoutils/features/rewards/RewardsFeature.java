package info.avicia.avoutils.features.rewards;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.api.ApiException;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.gui.UiStyle;
import info.avicia.avoutils.core.util.ClientThread;
import info.avicia.avoutils.core.util.PacketTextNormalizer;
import info.avicia.avoutils.core.util.WynnPillUtil;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.core.util.WynncraftServerPolicy.Scope;
import info.avicia.avoutils.features.rewards.RewardDistributor.Outcome;
import info.avicia.avoutils.features.rewards.api.PlayerRewards;
import info.avicia.avoutils.features.rewards.api.RewardsClient;
import info.avicia.avoutils.features.rewards.gui.RewardsPanel;
import info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout;
import info.avicia.avoutils.features.rewards.menu.GuildRewardMenu;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The pending-rewards panel beside Wynncraft's guild menus, for guild management. Its state lives here, because each
 * guild menu is a new screen: the list from the bot, the stepper, the status line and the hand-out run. Runs, locks
 * and fetches happen only on Wynncraft's main servers.
 */
public class RewardsFeature implements AvoFeature {
    /** {@code -Davoutils.debugRewards=true} logs the guild menu items and the chat lines a run sees. */
    static final boolean DEBUG = Boolean.getBoolean("avoutils.debugRewards");
    private static final long LIST_MAX_AGE_MS = 15_000;
    private static final List<RewardKind> BUILTIN_ORDER = List.of(RewardKind.LE, RewardKind.TOME, RewardKind.ASPECT);
    private static final Comparator<RewardKind> CUSTOM_ORDER = Comparator
            .comparing(RewardKind::apiName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(RewardKind::apiName);

    public record PlayerEntry(String uuid, String username, boolean inGuild, Map<RewardKind, Long> rewards) {
        public String displayName() {
            return username != null ? username : uuid;
        }
    }

    /** Recording a hand-out by hand, of up to {@code max}: what's owed. The panel's slider picks the amount. */
    public record Stepper(String uuid, RewardKind kind, long max) {}

    public record StatusLine(String text, int color) {}

    /** A player's tomes or aspects to hand out, by ▶ or as part of "Hand out all". */
    private record HandOut(PlayerEntry player, RewardKind kind) {}

    private RewardsClient client;
    private ClaimRecorder recorder;
    private RewardsPanel panel;

    private List<PlayerEntry> players = List.of();
    private boolean fetching;
    private long lastFetchStartMs;
    private long lastClaimReplyMs;
    private String listError;
    /** The panel shows only once the bot answered a fetch with a 200; a 403 hides it until a disconnect. */
    private boolean hasAccess;
    private boolean notManagement;
    private AbstractContainerScreen<?> lastScreen;

    private boolean collapsed;
    private Stepper stepper;
    private StatusLine status;
    private StatusLine warning;

    private RewardDistributor run;
    private String runUuid;
    private boolean lockPending;
    private boolean stopRequested;
    /** "Hand out all": the hand-outs after the current one, and how many there were in all (0 when it isn't on). */
    private final Deque<HandOut> queue = new ArrayDeque<>();
    private int queueTotal;
    private boolean queueSkippedAny;
    private int version;

    @Override
    public void initialize(ModConfig config) {
        client = new RewardsClient(config);
        recorder = new ClaimRecorder(client, ClientThread::run, new RecorderListener());
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> ClientThread.run(this::onDisconnect));
    }

    @Override
    public void onServerScopeChanged(Scope newScope) {
        if (newScope != Scope.MAIN) {
            ClientThread.run(() -> stopEverything("Left the main Wynncraft servers."));
        }
    }

    private void tick() {
        recorder.tick();
        if (run != null) {
            run.tick();
        }
    }

    private void onDisconnect() {
        stopEverything("Disconnected.");
        hasAccess = false;
        notManagement = false;
        players = List.of();
        listError = null;
        lastFetchStartMs = 0;
        lastScreen = null;
        stepper = null;
        status = null;
        warning = null;
        changed();
    }

    /** Called from ClientPacketListenerMixin for every system chat message, on the client thread. */
    public void onSystemChat(Component message) {
        if (run == null) return;
        if (DEBUG) {
            AvoUtilsMod.LOGGER.info("[AvoUtils] [Rewards] Chat: {}",
                    PacketTextNormalizer.normalizeForParsing(message.getString()));
        }
        run.onChat(message);
    }

    // ── Container screen hooks (AbstractContainerScreenMixin) ──────────

    public void renderPanel(AbstractContainerScreen<?> screen, GuiGraphics graphics, int mouseX, int mouseY,
                            float delta, int leftPos, int topPos, int imageHeight) {
        if (!isGuildScreen(screen) || notManagement || !WynncraftServerPolicy.isNetworkingAllowed()) return;
        if (screen != lastScreen) {
            lastScreen = screen;
            if (System.currentTimeMillis() - lastFetchStartMs >= LIST_MAX_AGE_MS) {
                refresh();
            }
        }
        if (!hasAccess) return;
        if (panel == null) {
            panel = new RewardsPanel(this);
        }
        panel.render(graphics, mouseX, mouseY, delta,
                new RewardsPanelLayout(screen.width, screen.height, leftPos, topPos, imageHeight));
    }

    /** True if the click is used up: it was on the panel, or a run is going, which slot clicks would disturb. */
    public boolean onMouseClicked(AbstractContainerScreen<?> screen, MouseButtonEvent click, boolean doubleClick) {
        if (!isGuildScreen(screen)) return false;
        boolean onPanel = panel != null && panelVisible() && panel.mouseClicked(click, doubleClick);
        return onPanel || run != null;
    }

    /** During a run, releases are swallowed too: vanilla can still send a click from one, e.g. after a double click. */
    public boolean onMouseReleased(AbstractContainerScreen<?> screen, MouseButtonEvent click) {
        boolean endedDrag = panel != null && panel.mouseReleased(click);
        return (endedDrag || run != null) && isGuildScreen(screen);
    }

    /** Drags of the panel's slider; the menu doesn't see them. */
    public boolean onMouseDragged(AbstractContainerScreen<?> screen, MouseButtonEvent event, double dragX,
                                  double dragY) {
        return isGuildScreen(screen) && panel != null && panelVisible() && panel.mouseDragged(event, dragX, dragY);
    }

    public boolean onMouseScrolled(AbstractContainerScreen<?> screen, double mouseX, double mouseY, double amount) {
        return isGuildScreen(screen) && panel != null && panelVisible() && panel.mouseScrolled(mouseX, mouseY, amount);
    }

    /** During a run every key but Esc is swallowed, so a number key can't send a reward by hand. */
    public boolean onKeyPressed(AbstractContainerScreen<?> screen, KeyEvent key) {
        return run != null && !key.isEscape() && isGuildScreen(screen);
    }

    private static boolean isGuildScreen(AbstractContainerScreen<?> screen) {
        return GuildRewardMenu.screenOf(screen.getTitle().getString()) != null;
    }

    private boolean panelVisible() {
        return hasAccess && !notManagement && WynncraftServerPolicy.isNetworkingAllowed();
    }

    // ── State for the panel ─────────────────────────────────────────────

    /** Changes whenever the panel's buttons must be rebuilt. */
    public int version() {
        return version;
    }

    public List<PlayerEntry> players() {
        return players;
    }

    public boolean isLoading() {
        return fetching;
    }

    public String listError() {
        return listError;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public Stepper stepper() {
        return stepper;
    }

    /** The player whose tomes or aspects are being handed out, for the panel to mark their row; null if none. */
    public String activeUuid() {
        return run != null ? runUuid : null;
    }

    /** How far the hand-out is, from 0 to 1, for the progress bar; negative when there's no bar to show. */
    public double progressRatio() {
        return run != null ? run.progressRatio() : -1;
    }

    public boolean isBusy() {
        return run != null || lockPending || queueTotal > 0;
    }

    public boolean canStartRun() {
        return !isBusy() && WynncraftServerPolicy.isNetworkingAllowed();
    }

    /** Whether "Hand out all" has anything to do: tomes or aspects for someone still in the guild. */
    public boolean canHandOutAll() {
        return canStartRun() && players.stream().anyMatch(player -> player.inGuild()
                && player.rewards().keySet().stream().anyMatch(RewardKind::handedOutByMod));
    }

    /** "Hand out all" progress, the run's progress or the last result, the last warning, what isn't recorded yet. */
    public List<StatusLine> statusLines() {
        List<StatusLine> lines = new ArrayList<>();
        if (queueTotal > 0) {
            lines.add(new StatusLine("Handing out everything: " + (queueTotal - queue.size()) + " of " + queueTotal,
                    UiStyle.TEXT_WHITE));
        }
        if (run != null) {
            lines.add(new StatusLine(run.progress(), UiStyle.TEXT_WHITE));
        } else if (status != null) {
            lines.add(status);
        }
        if (warning != null) {
            lines.add(warning);
        }
        for (String backlog : recorder.backlog()) {
            lines.add(new StatusLine(backlog, UiStyle.STATUS_WARNING));
        }
        return lines;
    }

    // ── Actions ─────────────────────────────────────────────────────────

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        changed();
    }

    public void refresh() {
        if (fetching || !WynncraftServerPolicy.isNetworkingAllowed()) return;
        fetching = true;
        long startedAt = System.currentTimeMillis();
        lastFetchStartMs = startedAt;
        changed();
        client.fetchPending().whenComplete((response, error) -> ClientThread.run(() -> {
            fetching = false;
            Throwable cause = unwrap(error);
            if (cause == null) {
                hasAccess = true;
                listError = null;
                // A claim reply that came while this loaded is fresher than this list
                if (startedAt >= lastClaimReplyMs || players.isEmpty()) {
                    players = toEntries(response.players);
                }
            } else if (cause instanceof ApiException api && api.status() == 403) {
                hasAccess = false;
                notManagement = true;
            } else {
                listError = "Couldn't load the list: " + message(cause);
                AvoUtilsMod.LOGGER.warn("[AvoUtils] [Rewards] Fetching pending rewards failed", cause);
            }
            changed();
        }));
    }

    /** ▶: hands out a player's tomes or aspects. */
    public void startRun(PlayerEntry player, RewardKind kind) {
        if (!canStartRun() || !player.inGuild() || !kind.handedOutByMod() || !isGuildMember()) return;
        warning = null;
        handOut(new HandOut(player, kind));
    }

    /** "Hand out all": every tome and aspect on the list, one player after another. LE and custom items are traded by hand. */
    public void startHandOutAll() {
        if (!canHandOutAll() || !isGuildMember()) return;
        queue.clear();
        for (PlayerEntry player : players) {
            if (!player.inGuild()) continue;
            for (RewardKind kind : player.rewards().keySet()) {
                if (kind.handedOutByMod()) {
                    queue.add(new HandOut(player, kind));
                }
            }
        }
        queueTotal = queue.size();
        queueSkippedAny = false;
        warning = null;
        handOut(queue.poll());
    }

    /** Sends every emerald in storage to the first head of the member list, the owner. Not a bot reward. */
    public void startEmeraldRun() {
        if (!canStartRun() || !isGuildMember()) return;
        warning = null;
        run = RewardDistributor.forEmeralds(new MinecraftGame(), new RewardDistributor.Listener() {
            @Override
            public void onConfirmed(long amount) {
            }

            @Override
            public void onFinished(String result, Outcome outcome) {
                run = null;
                setStatus(result, outcome == Outcome.DONE ? UiStyle.STATUS_SUCCESS : UiStyle.STATUS_WARNING);
            }
        });
        changed();
        run.start();
    }

    /** Stop: ends the run, and with it "Hand out all". */
    public void stopRun() {
        stopRequested = true;
        queue.clear();
        if (run != null) {
            run.stop();
        }
        changed();
    }

    private void stopEverything(String reason) {
        stopRequested = true;
        queue.clear();
        if (run != null) {
            run.abort(reason);
        }
        queueTotal = 0;
        changed();
    }

    private boolean isGuildMember() {
        if (AvoAuthService.getInstance().isGuildMember()) return true;
        setStatus("Only guild members can hand out rewards.", UiStyle.STATUS_ERROR);
        return false;
    }

    /** Takes the bot's lock for a player's tomes or aspects, then runs for what's pending now. */
    private void handOut(HandOut handOut) {
        String uuid = handOut.player().uuid();
        RewardKind kind = handOut.kind();
        String name = handOut.player().displayName();
        stopRequested = false;
        lockPending = true;
        setStatus("Checking what " + name + " is owed…", UiStyle.TEXT_WHITE);
        recorder.lock(uuid, kind, name, (response, error) -> {
            lockPending = false;
            boolean locked = error == null && response.ok && response.pending != null && response.pending > 0;
            if (stopRequested || (locked && !WynncraftServerPolicy.isNetworkingAllowed())) {
                if (locked) {
                    recorder.runFinished(uuid, kind); // releases the lock just taken
                }
                handOutEnded(handOut, stopRequested ? "Stopped." : "Left the main Wynncraft servers.",
                        Outcome.INTERRUPTED);
            } else if (locked) {
                runUuid = uuid;
                run = new RewardDistributor(new MinecraftGame(), new RunListener(handOut), kind, parseUuid(uuid),
                        handOut.player().username(), response.pending);
                changed();
                run.start();
            } else if (error != null) {
                handOutEnded(handOut, lockError(error), Outcome.INTERRUPTED);
            } else if (!response.ok) {
                String holder = response.lockedBy != null ? response.lockedBy : "Another Chief";
                handOutEnded(handOut, holder + " is already handing out " + kind.plural() + " to " + name + ".",
                        Outcome.PLAYER_SKIPPED);
            } else {
                handOutEnded(handOut, "Nothing is pending for " + name + " any more.", Outcome.PLAYER_SKIPPED);
                refresh();
            }
        });
    }

    /** A hand-out is over: show how it went, and go on with "Hand out all" unless something went wrong. */
    private void handOutEnded(HandOut handOut, String result, Outcome outcome) {
        if (queueTotal == 0) {
            setStatus(result, outcome == Outcome.DONE ? UiStyle.STATUS_SUCCESS : UiStyle.STATUS_WARNING);
            return;
        }
        if (outcome == Outcome.PLAYER_SKIPPED || outcome == Outcome.REWARD_UNAVAILABLE) {
            queueSkippedAny = true;
            WynnPillUtil.sendToPlayer(WynnPillUtil.createPrefixedPill("AvoUtils", false)
                    .append(Component.literal(result).withStyle(ChatFormatting.GRAY)));
        }
        if (outcome == Outcome.REWARD_UNAVAILABLE) {
            queue.removeIf(next -> next.kind().equals(handOut.kind()));
        } else if (outcome == Outcome.INTERRUPTED) {
            queue.clear();
        }
        HandOut next = queue.poll();
        if (next != null) {
            handOut(next);
            return;
        }
        queueTotal = 0;
        if (outcome == Outcome.INTERRUPTED || stopRequested) {
            setStatus(result + " Hand out all stopped there.", UiStyle.STATUS_WARNING);
        } else if (queueSkippedAny) {
            setStatus("Handed out everything the mod could. Some rewards are still pending; see chat.",
                    UiStyle.STATUS_WARNING);
        } else {
            setStatus("Handed out everything the mod can.", UiStyle.STATUS_SUCCESS);
        }
    }

    public void openStepper(PlayerEntry player, RewardKind kind) {
        long owed = player.rewards().getOrDefault(kind, 0L);
        stepper = owed > 0 ? new Stepper(player.uuid(), kind, owed) : null;
        changed();
    }

    public void closeStepper() {
        stepper = null;
        changed();
    }

    /** Records {@code amount} of the stepper's reward as handed out by hand, e.g. LE traded from the guild bank. */
    public void recordStepper(long amount) {
        if (stepper == null || !WynncraftServerPolicy.isNetworkingAllowed()) return;
        recorder.add(stepper.uuid(), stepper.kind(), nameOf(stepper.uuid()),
                Math.max(1, Math.min(stepper.max(), amount)));
        stepper = null;
        status = null;
        warning = null;
        changed();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    static List<PlayerEntry> toEntries(List<PlayerRewards> list) {
        if (list == null) return List.of();
        List<PlayerEntry> result = new ArrayList<>();
        for (PlayerRewards player : list) {
            Map<RewardKind, Long> raw = new LinkedHashMap<>();
            if (player.rewards != null) {
                player.rewards.forEach((type, amount) -> {
                    RewardKind kind = RewardKind.fromApiName(type);
                    if (kind != null && amount != null && amount > 0) {
                        raw.merge(kind, amount, Long::sum);
                    }
                });
            }
            Map<RewardKind, Long> rewards = orderRewards(raw);
            if (player.uuid != null && !rewards.isEmpty()) {
                result.add(new PlayerEntry(player.uuid, player.username, player.inGuild, rewards));
            }
        }
        return List.copyOf(result);
    }

    /** Places LE, Tome, Aspect first, followed by custom freeform rewards sorted alphabetically. */
    private static Map<RewardKind, Long> orderRewards(Map<RewardKind, Long> raw) {
        if (raw.isEmpty()) return Map.of();
        Map<RewardKind, Long> ordered = new LinkedHashMap<>();
        for (RewardKind builtin : BUILTIN_ORDER) {
            Long amount = raw.get(builtin);
            if (amount != null && amount > 0) {
                ordered.put(builtin, amount);
            }
        }
        raw.entrySet().stream()
                .filter(entry -> entry.getKey().isCustom() && entry.getValue() != null && entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey(CUSTOM_ORDER))
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(ordered);
    }

    /** Shows what a claim left pending, since claim replies are fresher than the list. */
    private void updatePending(String uuid, RewardKind kind, long pending) {
        lastClaimReplyMs = System.currentTimeMillis();
        List<PlayerEntry> updated = new ArrayList<>();
        for (PlayerEntry player : players) {
            if (!player.uuid().equals(uuid)) {
                updated.add(player);
                continue;
            }
            Map<RewardKind, Long> raw = new LinkedHashMap<>(player.rewards());
            if (pending > 0) {
                raw.put(kind, pending);
            } else {
                raw.remove(kind);
            }
            Map<RewardKind, Long> rewards = orderRewards(raw);
            if (!rewards.isEmpty()) {
                updated.add(new PlayerEntry(player.uuid(), player.username(), player.inGuild(), rewards));
            }
        }
        players = List.copyOf(updated);
        if (stepper != null && stepper.uuid().equals(uuid) && stepper.kind().equals(kind)) {
            stepper = pending > 0 ? new Stepper(uuid, kind, pending) : null;
        }
        changed();
    }

    private String nameOf(String uuid) {
        for (PlayerEntry player : players) {
            if (player.uuid().equals(uuid)) return player.displayName();
        }
        return uuid;
    }

    private String lockError(Throwable error) {
        if (error instanceof ApiException api && api.status() == 404) {
            return "The bot needs an update to hand out rewards.";
        }
        if (error instanceof ApiException api && api.status() == 403) {
            notManagement = true;
            return "Only guild management can hand out rewards.";
        }
        return "Couldn't reach the bot: " + message(error);
    }

    /** Warns in chat and in the status area, until the next hand-out or recording. */
    private void warn(String text) {
        warning = new StatusLine(text, UiStyle.STATUS_ERROR);
        changed();
        WynnPillUtil.sendToPlayer(WynnPillUtil.createPrefixedPill("AvoUtils", true)
                .append(Component.literal(text).withStyle(ChatFormatting.RED)));
    }

    private void setStatus(String text, int color) {
        status = new StatusLine(text, color);
        changed();
    }

    private void changed() {
        version++;
    }

    private static UUID parseUuid(String uuid) {
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    private static String message(Throwable error) {
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }

    private final class RunListener implements RewardDistributor.Listener {
        private final HandOut handOut;

        RunListener(HandOut handOut) {
            this.handOut = handOut;
        }

        @Override
        public void onConfirmed(long amount) {
            recorder.add(handOut.player().uuid(), handOut.kind(), handOut.player().displayName(), amount);
        }

        @Override
        public void onFinished(String result, Outcome outcome) {
            run = null;
            runUuid = null;
            recorder.runFinished(handOut.player().uuid(), handOut.kind());
            handOutEnded(handOut, result, outcome);
        }
    }

    private final class RecorderListener implements ClaimRecorder.Listener {
        @Override
        public void onRecorded(String uuid, RewardKind kind, long pending) {
            updatePending(uuid, kind, pending);
        }

        @Override
        public void onExcess(String uuid, RewardKind kind, String name, long excess) {
            updatePending(uuid, kind, 0);
            warn(name + " was handed " + kind.label(excess) + " more than pending. The extra isn't recorded.");
            stopRunFor(uuid, kind);
        }

        @Override
        public void onClaimFailed(String uuid, RewardKind kind, String name, long amount, String error) {
            warn("Couldn't record " + kind.label(amount) + " for " + name + ": " + error);
            stopRunFor(uuid, kind);
        }

        private void stopRunFor(String uuid, RewardKind kind) {
            if (run != null && uuid.equals(runUuid) && run.kind().equals(kind)) {
                run.stop();
            }
        }
    }
}
