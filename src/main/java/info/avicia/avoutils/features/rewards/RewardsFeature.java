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
import info.avicia.avoutils.features.rewards.api.PlayerRewards;
import info.avicia.avoutils.features.rewards.api.RewardsClient;
import info.avicia.avoutils.features.rewards.gui.RewardsPanel;
import info.avicia.avoutils.features.rewards.gui.RewardsPanelLayout;
import info.avicia.avoutils.features.rewards.menu.GuildRewardMenu;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public record PlayerEntry(String uuid, String username, boolean inGuild, Map<RewardKind, Long> rewards) {
        public String displayName() {
            return username != null ? username : uuid;
        }
    }

    /** Recording a hand-out by hand: {@code amount} of {@code max} owed. */
    public record Stepper(String uuid, RewardKind kind, long amount, long max) {}

    public record StatusLine(String text, int color) {}

    private RewardsClient client;
    private ClaimRecorder recorder;
    private RewardsPanel panel;

    private List<PlayerEntry> players = List.of();
    private final Set<String> loggedUnknownTypes = new HashSet<>();
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
    /** Kept apart from the status, which a run's progress and result replace. */
    private StatusLine warning;

    private RewardDistributor run;
    private String runUuid;
    private boolean lockPending;
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
            ClientThread.run(() -> {
                if (run != null) {
                    run.abort("Left the main Wynncraft servers.");
                }
            });
        }
    }

    private void tick() {
        recorder.tick();
        if (run != null) {
            run.tick();
        }
    }

    private void onDisconnect() {
        if (run != null) {
            run.abort("Disconnected.");
        }
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
    public boolean onMouseReleased(AbstractContainerScreen<?> screen) {
        return run != null && isGuildScreen(screen);
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

    public boolean isRunActive() {
        return run != null;
    }

    public boolean canStartRun() {
        return run == null && !lockPending && WynncraftServerPolicy.isNetworkingAllowed();
    }

    /** The run's progress or the last result, the last warning, then what isn't recorded yet. */
    public List<StatusLine> statusLines() {
        List<StatusLine> lines = new ArrayList<>();
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
                    players = entries(response.players);
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

    /** Hands out a player's tomes or aspects: takes the bot's lock, then runs for what's pending now. */
    public void startRun(PlayerEntry player, RewardKind kind) {
        if (!canStartRun() || !player.inGuild() || !kind.handedOutByMod()) return;
        if (!AvoAuthService.getInstance().isGuildMember()) {
            setStatus("Only guild members can hand out rewards.", UiStyle.STATUS_ERROR);
            return;
        }
        String name = player.displayName();
        lockPending = true;
        warning = null;
        setStatus("Checking what " + name + " is owed…", UiStyle.TEXT_WHITE);
        recorder.lock(player.uuid(), kind, name, (response, error) -> {
            lockPending = false;
            if (error != null) {
                setStatus(lockError(error), UiStyle.STATUS_ERROR);
            } else if (!response.ok) {
                String holder = response.lockedBy != null ? response.lockedBy : "Another Chief";
                setStatus(holder + " is already handing out " + kind.plural() + " to " + name + ".",
                        UiStyle.STATUS_WARNING);
            } else if (response.pending == null || response.pending <= 0) {
                setStatus("Nothing is pending for " + name + " any more.", UiStyle.STATUS_WARNING);
                refresh();
            } else if (!WynncraftServerPolicy.isNetworkingAllowed()) {
                recorder.runFinished(player.uuid(), kind); // releases the lock just taken
                setStatus("Left the main Wynncraft servers.", UiStyle.STATUS_WARNING);
            } else {
                runUuid = player.uuid();
                run = new RewardDistributor(new MinecraftGame(), new RunListener(player, kind), kind,
                        parseUuid(player.uuid()), player.username(), response.pending);
                changed();
                run.start();
            }
        });
    }

    /** Sends every emerald in storage to the first head of the member list, the owner. Not a bot reward. */
    public void startEmeraldRun() {
        if (!canStartRun()) return;
        if (!AvoAuthService.getInstance().isGuildMember()) {
            setStatus("Only guild members can hand out rewards.", UiStyle.STATUS_ERROR);
            return;
        }
        warning = null;
        run = RewardDistributor.forEmeralds(new MinecraftGame(), new RewardDistributor.Listener() {
            @Override
            public void onConfirmed(long amount) {
            }

            @Override
            public void onFinished(String result, boolean complete) {
                run = null;
                setStatus(result, complete ? UiStyle.STATUS_SUCCESS : UiStyle.STATUS_WARNING);
            }
        });
        changed();
        run.start();
    }

    public void stopRun() {
        if (run != null) {
            run.stop();
        }
    }

    public void openStepper(PlayerEntry player, RewardKind kind) {
        long owed = player.rewards().getOrDefault(kind, 0L);
        stepper = owed > 0 ? new Stepper(player.uuid(), kind, owed, owed) : null;
        changed();
    }

    public void stepBy(long delta) {
        if (stepper == null) return;
        long amount = Math.max(1, Math.min(stepper.max(), stepper.amount() + delta));
        stepper = new Stepper(stepper.uuid(), stepper.kind(), amount, stepper.max());
        changed();
    }

    public void closeStepper() {
        stepper = null;
        changed();
    }

    /** Records the stepper's amount as handed out, e.g. LE traded from the guild bank. */
    public void recordStepper() {
        if (stepper == null || !WynncraftServerPolicy.isNetworkingAllowed()) return;
        recorder.add(stepper.uuid(), stepper.kind(), nameOf(stepper.uuid()), stepper.amount());
        stepper = null;
        status = null;
        warning = null;
        changed();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private List<PlayerEntry> entries(List<PlayerRewards> list) {
        if (list == null) return List.of();
        List<PlayerEntry> result = new ArrayList<>();
        for (PlayerRewards player : list) {
            Map<RewardKind, Long> rewards = new EnumMap<>(RewardKind.class);
            if (player.rewards != null) {
                player.rewards.forEach((type, amount) -> {
                    RewardKind kind = RewardKind.fromApiName(type);
                    if (kind == null) {
                        if (loggedUnknownTypes.add(type)) {
                            AvoUtilsMod.LOGGER.info("[AvoUtils] [Rewards] Skipping unknown reward type '{}'", type);
                        }
                    } else if (amount != null && amount > 0) {
                        rewards.put(kind, amount);
                    }
                });
            }
            if (player.uuid != null && !rewards.isEmpty()) {
                result.add(new PlayerEntry(player.uuid, player.username, player.inGuild, rewards));
            }
        }
        return List.copyOf(result);
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
            Map<RewardKind, Long> rewards = new EnumMap<>(RewardKind.class);
            rewards.putAll(player.rewards());
            if (pending > 0) {
                rewards.put(kind, pending);
            } else {
                rewards.remove(kind);
            }
            if (!rewards.isEmpty()) {
                updated.add(new PlayerEntry(player.uuid(), player.username(), player.inGuild(), rewards));
            }
        }
        players = List.copyOf(updated);
        if (stepper != null && stepper.uuid().equals(uuid) && stepper.kind() == kind) {
            stepper = pending > 0
                    ? new Stepper(uuid, kind, Math.min(stepper.amount(), pending), pending)
                    : null;
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
        private final PlayerEntry player;
        private final RewardKind kind;

        RunListener(PlayerEntry player, RewardKind kind) {
            this.player = player;
            this.kind = kind;
        }

        @Override
        public void onConfirmed(long amount) {
            recorder.add(player.uuid(), kind, player.displayName(), amount);
        }

        @Override
        public void onFinished(String result, boolean complete) {
            run = null;
            runUuid = null;
            recorder.runFinished(player.uuid(), kind);
            setStatus(result, complete ? UiStyle.STATUS_SUCCESS : UiStyle.STATUS_WARNING);
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
            if (run != null && uuid.equals(runUuid) && run.kind() == kind) {
                run.stop();
            }
        }
    }
}
