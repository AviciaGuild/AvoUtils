package info.avicia.avoutils.features.rewards;

import info.avicia.avoutils.core.chat.GuildChat;
import info.avicia.avoutils.core.chat.RewardDetector;
import info.avicia.avoutils.core.util.PacketTextNormalizer;
import info.avicia.avoutils.features.rewards.menu.GuildRewardMenu;
import info.avicia.avoutils.features.rewards.menu.GuildRewardMenu.GuildScreen;
import info.avicia.avoutils.features.rewards.menu.GuildRewardMenu.MenuItem;
import info.avicia.avoutils.features.rewards.menu.GuildRewardMenu.RewardAction;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/**
 * Hands out tomes, aspects or emeralds to one member by pressing Wynncraft's reward key on their head in the guild
 * menus: one press per unit at each reward's pace ({@link #pressIntervalTicks}), never re-sent, so a run can't hand out
 * more than owed. A grant counts once it shows up in guild chat; anything that doesn't stays pending. Driven by
 * client ticks; the game is behind {@link Game}, so tests can play it.
 */
public final class RewardDistributor {
    /** Confirmations still count for this long after pressing stops. */
    static final int LATE_CONFIRMATION_TICKS = 40;
    /** Before the menu is read, it must stay the same this long; after a click it must change first. */
    static final int MENU_STABLE_TICKS = 4;
    static final int MENU_TIMEOUT_TICKS = 80;
    static final int MAX_PAGE_CHANGES = 12;

    private static final Pattern NOT_ENOUGH =
            Pattern.compile("(?i)your guild does not have enough .+? to send a reward");
    private static final Pattern NO_PERMISSION = Pattern.compile("(?i)you do not have permission to send this reward");

    /** The open guild menu and the local player, as the distributor needs them. */
    public interface Game {
        /** The guild menu that's open, or null if none is. */
        GuildScreen screen();

        int containerId();

        int stateId();

        List<MenuItem> items();

        /** Left-clicks a slot of the open menu. */
        void click(int slot);

        /** Presses number key {@code key} (1-9) over a slot of the open menu. */
        void pressKey(int slot, int key);

        boolean isSelf(String username);
    }

    /** How a run ended, so a queue of runs knows whether to go on. */
    public enum Outcome {
        /** Everything owed was handed out. */
        DONE,
        /** Some stays pending for this player, e.g. they aren't on the member list or not every grant showed up. */
        PLAYER_SKIPPED,
        /** The guild can't send this reward now: storage ran out, or the rank or permission is missing. */
        REWARD_UNAVAILABLE,
        /** Stopped, or the menu closed or misbehaved: nothing more should be sent. */
        INTERRUPTED
    }

    public interface Listener {
        /** Wynncraft confirmed a grant of {@code amount}. */
        void onConfirmed(long amount);

        void onFinished(String result, Outcome outcome);
    }

    /** {@code COUNTING_LATE}: pressing is over, but confirmations of presses already sent still count. */
    private enum State { WAITING, FINDING_HEAD, PRESSING, COUNTING_LATE, DONE }

    private final Game game;
    private final Listener listener;
    private final RewardKind kind;
    private final UUID uuid;
    private final String username;
    /** Long.MAX_VALUE for emeralds, which go until storage runs out. */
    private final long owed;
    private final int pressInterval;

    private State state = State.FINDING_HEAD;
    private long now;
    private int ticksInState;

    private int containerIdBefore;
    private int stateIdBefore;
    private int lastContainerId;
    private int lastStateId;
    private int stableTicks;
    private boolean mustChange;
    private int pageChanges;
    private Boolean startedOnFirstPage;
    private boolean searchingBack;

    private int headSlot;
    private String headName;
    private UUID headProfileId;
    private String headProfileName;
    private int key;
    private long plannedPresses = -1;

    private long pressesSent;
    private long confirmedPresses;
    private long failedPresses;
    private long confirmedAmount;
    private long nextPressTick;
    private long stoppedPressingTick;
    private String stopReason;
    private Outcome stopOutcome;
    private boolean storageEmpty;

    /** Hands out {@code owed} of {@code kind} to the member with this UUID or name, once {@link #start} is called. */
    public RewardDistributor(Game game, Listener listener, RewardKind kind, UUID uuid, String username, long owed) {
        this.game = game;
        this.listener = listener;
        this.kind = kind;
        this.uuid = uuid;
        this.username = username;
        this.owed = owed;
        this.pressInterval = pressIntervalTicks(kind);
    }

    /** Sends every emerald in storage to the first head of the member list, the owner. */
    public static RewardDistributor forEmeralds(Game game, Listener listener) {
        return new RewardDistributor(game, listener, RewardKind.EMERALDS, null, null, Long.MAX_VALUE);
    }

    /** Ticks between presses. */
    static int pressIntervalTicks(RewardKind kind) {
        if (kind.equals(RewardKind.TOME)) return 10;
        if (kind.equals(RewardKind.ASPECT) || kind.equals(RewardKind.EMERALDS)) return 4;
        throw new IllegalArgumentException(kind + " is handed out by trading, not by the mod");
    }

    /** Opens the member list if the Manage menu is open; the search starts on the next tick. */
    public void start() {
        GuildScreen screen = game.screen();
        if (screen == GuildScreen.MEMBERS) {
            transition(State.FINDING_HEAD);
            return;
        }
        MenuItem entry = screen == GuildScreen.MANAGE ? GuildRewardMenu.findGuildEntry(game.items()) : null;
        if (entry == null) {
            fail("Can't find the guild's member list in this menu.", Outcome.INTERRUPTED);
            return;
        }
        clickAndWait(entry.slot());
    }

    public boolean isRunning() {
        return state != State.DONE;
    }

    public RewardKind kind() {
        return kind;
    }

    /** What the run is doing, for the status line. */
    public String progress() {
        if (state == State.DONE) return "";
        if (pressesSent == 0 && state != State.PRESSING) return "Finding " + who() + "…";
        return kind == RewardKind.EMERALDS
                ? "Sending emeralds to " + targetLabel() + ": " + String.format(Locale.US, "%,d", confirmedAmount)
                : "Giving " + targetLabel() + " " + kind.plural() + " " + confirmedAmount + "/" + owed;
    }

    /** How far the run is, from 0 to 1, for a progress bar; -1 while it's finding the head, and for emeralds. */
    public double progressRatio() {
        if (state == State.DONE || kind == RewardKind.EMERALDS || (pressesSent == 0 && state != State.PRESSING)) {
            return -1;
        }
        return owed <= 0 ? 1 : Math.min(1, (double) confirmedAmount / owed);
    }

    public void tick() {
        if (state == State.DONE) return;
        now++;
        ticksInState++;
        GuildScreen screen = game.screen();
        boolean menuGone = state == State.WAITING ? screen == null : screen != GuildScreen.MEMBERS;
        if (state != State.COUNTING_LATE && menuGone) {
            endWith("The menu was closed.", Outcome.INTERRUPTED);
            return;
        }
        switch (state) {
            case WAITING -> tickWaiting();
            case FINDING_HEAD -> findHead();
            case PRESSING -> tickPressing();
            case COUNTING_LATE -> tickCountingLate();
            default -> {
            }
        }
    }

    /** Counts the run's own grants and notices Wynncraft's refusals. */
    public void onChat(Component message) {
        if (state == State.DONE || pressesSent == 0 || message == null) return;
        String cleaned = PacketTextNormalizer.normalizeForParsing(message.getString());
        boolean notEnough = isRefusal(NOT_ENOUGH, cleaned);
        if (notEnough || isRefusal(NO_PERMISSION, cleaned)) {
            if (outstanding() > 0) {
                failedPresses++;
            }
            if (state != State.COUNTING_LATE) {
                storageEmpty = notEnough;
                stopPressing(notEnough
                        ? (kind == RewardKind.EMERALDS ? null : "The guild ran out of " + kind.plural() + ".")
                        : "You don't have permission to send " + kind.plural() + ".", Outcome.REWARD_UNAVAILABLE);
            }
            return;
        }
        RewardDetector.RewardResult grant = RewardDetector.tryDetect(cleaned, message);
        if (grant == null || !GuildChat.isGuildChat(message) || !game.isSelf(grant.sender())
                || !isTarget(grant.recipient())) {
            return;
        }
        long amount = kind.equals(RewardKind.EMERALDS) ? grant.emeraldAmount()
                : kind.equals(RewardKind.ASPECT) ? grant.aspectAmount()
                : kind.equals(RewardKind.TOME) ? grant.tomeCount() : 0;
        if (amount <= 0) return;
        confirmedPresses++;
        confirmedAmount += amount;
        listener.onConfirmed(amount);
    }

    /** The Chief's Stop: no more presses, but late confirmations still count. */
    public void stop() {
        if (state == State.PRESSING || state == State.WAITING || state == State.FINDING_HEAD) {
            endWith("Stopped.", Outcome.INTERRUPTED);
        }
    }

    /** Ends the run at once, e.g. on a disconnect, when no more confirmations can come. */
    public void abort(String reason) {
        if (state == State.DONE) return;
        if (pressesSent == 0) {
            fail(reason, Outcome.INTERRUPTED);
            return;
        }
        if (stopReason == null) stopReason = reason;
        if (stopOutcome == null) stopOutcome = Outcome.INTERRUPTED;
        reportResult();
    }

    /** Wynncraft's refusal, not a player typing it: in a chat line, a colon comes before the text. */
    private static boolean isRefusal(Pattern refusal, String text) {
        Matcher matcher = refusal.matcher(text);
        return matcher.find() && text.lastIndexOf(':', matcher.start()) < 0;
    }

    private void clickAndWait(int slot) {
        waitForMenu(true);
        game.click(slot);
    }

    /** Waits until the menu stays the same for a few ticks, as pages load in parts; after a click it must change first. */
    private void waitForMenu(boolean afterClick) {
        containerIdBefore = lastContainerId = game.containerId();
        stateIdBefore = lastStateId = game.stateId();
        stableTicks = 0;
        mustChange = afterClick;
        transition(State.WAITING);
    }

    private void tickWaiting() {
        int containerId = game.containerId();
        int stateId = game.stateId();
        if (containerId != lastContainerId || stateId != lastStateId) {
            lastContainerId = containerId;
            lastStateId = stateId;
            stableTicks = 0;
        } else {
            stableTicks++;
        }
        boolean changed = !mustChange || containerId != containerIdBefore || stateId != stateIdBefore;
        if (changed && stableTicks >= MENU_STABLE_TICKS && game.screen() == GuildScreen.MEMBERS) {
            transition(State.FINDING_HEAD);
        } else if (ticksInState >= MENU_TIMEOUT_TICKS) {
            endWith("The member list didn't load.", Outcome.INTERRUPTED);
        }
    }

    private void findHead() {
        List<MenuItem> items = game.items();
        if (startedOnFirstPage == null) {
            startedOnFirstPage = GuildRewardMenu.findPreviousPage(items) == null;
        }
        MenuItem head;
        if (kind == RewardKind.EMERALDS) {
            MenuItem previous = GuildRewardMenu.findPreviousPage(items);
            if (previous != null) {
                turnPage(previous); // the owner is first on page 1
                return;
            }
            head = GuildRewardMenu.firstMember(items);
        } else {
            head = GuildRewardMenu.findMember(items, uuid, username);
        }
        if (head == null) {
            searchNextPage(items);
            return;
        }
        RewardAction action = GuildRewardMenu.rewardAction(head.lore(), kind);
        if (action == null) {
            endWith("Your rank can't send " + kind.plural() + ".", Outcome.REWARD_UNAVAILABLE);
            return;
        }
        headSlot = head.slot();
        headName = head.name();
        headProfileId = head.profileId();
        headProfileName = head.profileName() != null && !head.profileName().isBlank() ? head.profileName() : null;
        key = action.key();
        if (plannedPresses < 0) {
            plannedPresses = owed == Long.MAX_VALUE ? Long.MAX_VALUE : owed / action.amountPerPress();
            if (plannedPresses == 0) {
                fail("Each press sends " + kind.label(action.amountPerPress()) + ", more than the " + owed + " owed.",
                        Outcome.PLAYER_SKIPPED);
                return;
            }
        }
        transition(State.PRESSING);
    }

    /** Pages forward from where the search started, then back, unless it started on page 1. */
    private void searchNextPage(List<MenuItem> items) {
        if (!searchingBack) {
            MenuItem next = GuildRewardMenu.findNextPage(items);
            if (next != null) {
                turnPage(next);
                return;
            }
            searchingBack = !startedOnFirstPage;
        }
        MenuItem previous = searchingBack ? GuildRewardMenu.findPreviousPage(items) : null;
        if (previous == null) {
            endWith(capitalize(who()) + " isn't on the member list.", Outcome.PLAYER_SKIPPED);
            return;
        }
        turnPage(previous);
    }

    private void turnPage(MenuItem pageItem) {
        if (pageChanges >= MAX_PAGE_CHANGES) {
            endWith("Stopped after " + MAX_PAGE_CHANGES + " pages without finding " + who() + ".",
                    Outcome.PLAYER_SKIPPED);
            return;
        }
        pageChanges++;
        clickAndWait(pageItem.slot());
    }

    private void tickPressing() {
        if (pressesSent >= plannedPresses) {
            stopPressing(null, null);
            return;
        }
        if (now < nextPressTick) return;
        MenuItem item = itemAt(game.items(), headSlot);
        if (item == null || !item.name().equals(headName) || !Objects.equals(item.profileId(), headProfileId)) {
            findHeadAgain();
            return;
        }
        game.pressKey(headSlot, key);
        pressesSent++;
        nextPressTick = now + pressInterval;
    }

    /** The menu changed under the run, e.g. Wynncraft opened it again: once it settles, search from where it is. */
    private void findHeadAgain() {
        searchingBack = false;
        startedOnFirstPage = null;
        waitForMenu(false);
    }

    private void tickCountingLate() {
        if (outstanding() <= 0 || now - stoppedPressingTick >= LATE_CONFIRMATION_TICKS) {
            reportResult();
        }
    }

    private long outstanding() {
        return pressesSent - confirmedPresses - failedPresses;
    }

    /** A grant names the target by the bot's name for them or their head's profile name, else by the head's name. */
    private boolean isTarget(String recipient) {
        if (username == null && headProfileName == null) {
            return GuildRewardMenu.containsName(headName, recipient);
        }
        return (username != null && username.equalsIgnoreCase(recipient))
                || (headProfileName != null && headProfileName.equalsIgnoreCase(recipient));
    }

    private static MenuItem itemAt(List<MenuItem> items, int slot) {
        for (MenuItem item : items) {
            if (item.slot() == slot) return item;
        }
        return null;
    }

    /** Stops pressing; confirmations of presses already sent still count for a while. */
    private void stopPressing(String reason, Outcome outcome) {
        if (stopReason == null) stopReason = reason;
        if (stopOutcome == null) stopOutcome = outcome;
        stoppedPressingTick = now;
        transition(State.COUNTING_LATE);
    }

    /** Ends with {@code reason}: as a failure before the first press, otherwise by stopping to press. */
    private void endWith(String reason, Outcome outcome) {
        if (pressesSent > 0) {
            stopPressing(reason, outcome);
        } else {
            fail(reason, outcome);
        }
    }

    /** Ends at once, before any press. */
    private void fail(String message, Outcome outcome) {
        state = State.DONE;
        listener.onFinished(message, outcome);
    }

    private void reportResult() {
        state = State.DONE;
        boolean complete = kind == RewardKind.EMERALDS ? storageEmpty : confirmedAmount >= owed;
        Outcome outcome;
        if (complete) {
            outcome = Outcome.DONE;
        } else if (stopOutcome != null) {
            outcome = stopOutcome;
        } else {
            outcome = Outcome.PLAYER_SKIPPED; // every press went out; what didn't show up in guild chat stays pending
        }
        listener.onFinished(summary(), outcome);
    }

    private String summary() {
        StringBuilder text = new StringBuilder();
        if (stopReason != null) text.append(stopReason).append(' ');
        text.append("Sent ");
        if (kind == RewardKind.EMERALDS) {
            text.append(String.format(Locale.US, "%,d", confirmedAmount)).append(" emeralds");
        } else {
            text.append(confirmedAmount).append(" of ").append(owed).append(' ').append(kind.noun(owed));
        }
        return text.append(" to ").append(targetLabel()).append('.').toString();
    }

    private String targetLabel() {
        if (username != null) return username;
        if (headProfileName != null) return headProfileName;
        return headName != null ? PacketTextNormalizer.normalizeForParsing(headName) : who();
    }

    private String who() {
        if (username != null) return username;
        return kind == RewardKind.EMERALDS ? "the owner" : "the player";
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private void transition(State next) {
        state = next;
        ticksInState = 0;
    }
}
