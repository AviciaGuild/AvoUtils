package info.avicia.avoutils.features.rewards;

import info.avicia.avoutils.features.rewards.RewardDistributor.Outcome;
import info.avicia.avoutils.features.rewards.menu.GuildRewardMenu.GuildScreen;
import info.avicia.avoutils.features.rewards.menu.GuildRewardMenu.MenuItem;
import info.avicia.avoutils.testutil.TextFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardDistributorTest {

    private static final UUID STEVE_ID = UUID.fromString("0f1e2d3c-4b5a-4978-8796-a5b4c3d2e1f0");
    private static final List<String> LORE = List.of("Press 1 to send an Aspect", "Press 2 to send a Guild Tome",
            "Press 3 to send 1,024 Emeralds");
    private static final int GUILD_CHAT_COLOR = 0x55FFFF;

    private final FakeGame game = new FakeGame();
    private final List<Long> confirmed = new ArrayList<>();
    private String result;
    private Outcome outcome;
    private final RewardDistributor.Listener listener = new RewardDistributor.Listener() {
        @Override
        public void onConfirmed(long amount) {
            confirmed.add(amount);
        }

        @Override
        public void onFinished(String text, Outcome how) {
            result = text;
            outcome = how;
        }
    };

    /** A guild menu that answers clicks the way a test sets up. */
    private static final class FakeGame implements RewardDistributor.Game {
        GuildScreen screen = GuildScreen.MEMBERS;
        int containerId = 1;
        int stateId = 1;
        List<MenuItem> items = new ArrayList<>();
        IntConsumer onClick = slot -> {
        };
        final List<String> actions = new ArrayList<>();

        /** Wynncraft replacing the page's contents. */
        void show(List<MenuItem> page) {
            items = page;
            stateId++;
        }

        @Override
        public GuildScreen screen() {
            return screen;
        }

        @Override
        public int containerId() {
            return containerId;
        }

        @Override
        public int stateId() {
            return stateId;
        }

        @Override
        public List<MenuItem> items() {
            return items;
        }

        @Override
        public void click(int slot) {
            actions.add("click " + slot);
            onClick.accept(slot);
        }

        @Override
        public void pressKey(int slot, int key) {
            actions.add("press " + slot + " key " + key);
        }

        @Override
        public boolean isSelf(String username) {
            return "Chief".equalsIgnoreCase(username);
        }
    }

    private static MenuItem head(int slot, String name, UUID id) {
        return new MenuItem(slot, name, LORE, id, name);
    }

    private static MenuItem item(int slot, String name) {
        return new MenuItem(slot, name, List.of(), null, null);
    }

    private static Component guildLine(String text) {
        return TextFixtures.coloredText(text, GUILD_CHAT_COLOR);
    }

    private RewardDistributor start(RewardKind kind, long owed) {
        RewardDistributor run = new RewardDistributor(game, listener, kind, STEVE_ID, "Steve", owed);
        run.start();
        return run;
    }

    private static void ticks(RewardDistributor run, int count) {
        for (int i = 0; i < count; i++) {
            run.tick();
        }
    }

    private List<String> presses() {
        return game.actions.stream().filter(action -> action.startsWith("press")).toList();
    }

    /** Ticks between presses, read from the distributor so these tests follow its tuning. */
    private static int pace(RewardKind kind) {
        return RewardDistributor.pressIntervalTicks(kind);
    }

    @Test
    void refusesToHandOutLe() {
        assertThrows(IllegalArgumentException.class,
                () -> new RewardDistributor(game, listener, RewardKind.LE, STEVE_ID, "Steve", 1));
    }

    @Test
    void pressesOncePerUnitAtTheRewardsPaceAndFinishesWhenAllAreConfirmed() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.TOME, 3);
        assertEquals("Finding Steve…", run.progress());

        ticks(run, 2);
        assertEquals(List.of("press 3 key 2"), presses());
        run.onChat(guildLine("Chief rewarded a Guild Tome to Steve"));
        assertEquals("Giving Steve tomes 1/3", run.progress());
        ticks(run, pace(RewardKind.TOME) - 1);
        assertEquals(1, presses().size());
        ticks(run, 1);
        assertEquals(2, presses().size());
        run.onChat(guildLine("Chief rewarded a Guild Tome to Steve"));
        ticks(run, pace(RewardKind.TOME));
        assertEquals(3, presses().size());
        run.onChat(guildLine("Chief rewarded a Guild Tome to Steve"));
        ticks(run, 2);

        assertFalse(run.isRunning());
        assertEquals(List.of(1L, 1L, 1L), confirmed);
        assertEquals("Sent 3 of 3 tomes to Steve.", result);
        assertEquals(Outcome.DONE, outcome);
    }

    @Test
    void namesOneRewardInTheSingular() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.TOME, 1);
        ticks(run, 2);

        run.onChat(guildLine("Chief rewarded a Guild Tome to Steve"));
        ticks(run, 2);

        assertEquals("Sent 1 of 1 tome to Steve.", result);
    }

    @Test
    void reportsItsProgressForTheBar() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.TOME, 2);
        assertEquals(-1, run.progressRatio());

        ticks(run, 2);
        assertEquals(0, run.progressRatio());
        run.onChat(guildLine("Chief rewarded a Guild Tome to Steve"));
        assertEquals(0.5, run.progressRatio());

        RewardDistributor emeralds = RewardDistributor.forEmeralds(game, listener);
        emeralds.start();
        ticks(emeralds, 2);
        assertEquals(-1, emeralds.progressRatio());
    }

    @Test
    void pressesAtThePaceWithoutWaitingForConfirmations() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.ASPECT, 10);

        ticks(run, 2 + 2 * pace(RewardKind.ASPECT));

        assertEquals(3, presses().size());
    }

    @Test
    void pressesEachUnitOnceEvenIfNoGrantShowsUp() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.ASPECT, 3);

        ticks(run, 2 + 2 * pace(RewardKind.ASPECT));
        assertEquals(3, presses().size());
        ticks(run, RewardDistributor.LATE_CONFIRMATION_TICKS);
        assertTrue(run.isRunning());
        ticks(run, 1);

        assertFalse(run.isRunning());
        assertEquals(3, presses().size());
        assertEquals("Sent 0 of 3 aspects to Steve.", result);
        assertEquals(Outcome.PLAYER_SKIPPED, outcome);
    }

    @Test
    void countsLateConfirmationsAfterStop() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.TOME, 2);
        ticks(run, 2);

        run.stop();
        ticks(run, 30);
        run.onChat(guildLine("Chief rewarded a Guild Tome to Steve"));
        ticks(run, 1);

        assertEquals(1, presses().size());
        assertEquals(List.of(1L), confirmed);
        assertEquals("Stopped. Sent 1 of 2 tomes to Steve.", result);
        assertEquals(Outcome.INTERRUPTED, outcome);
    }

    @Test
    void stopsCountingTwoSecondsAfterPressingEnds() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.TOME, 2);
        ticks(run, 2);

        run.stop();
        ticks(run, 40);
        run.onChat(guildLine("Chief rewarded a Guild Tome to Steve"));

        assertFalse(run.isRunning());
        assertTrue(confirmed.isEmpty());
        assertEquals("Stopped. Sent 0 of 2 tomes to Steve.", result);
    }

    @Test
    void checksTheHeadBeforeEachPressAndFindsItAgainOnceTheMenuSettles() {
        game.items = List.of(head(3, "Steve", STEVE_ID), head(4, "Alex", null));
        RewardDistributor run = start(RewardKind.TOME, 2);
        ticks(run, 2);
        run.onChat(guildLine("Chief rewarded a Guild Tome to Steve"));

        game.show(List.of(head(3, "Alex", null), head(5, "Steve", STEVE_ID)));
        ticks(run, pace(RewardKind.TOME) + 5); // the press comes due, the menu stays still 4 ticks, the head is found
        assertEquals(1, presses().size());
        ticks(run, 1);

        assertEquals(List.of("press 3 key 2", "press 5 key 2"), presses());
    }

    @Test
    void opensTheMemberListFromManageAndWaitsUntilItStopsChanging() {
        game.screen = GuildScreen.MANAGE;
        game.items = List.of(item(4, "§bAvicia §7[Lv. 120]"));
        RewardDistributor run = start(RewardKind.TOME, 1);
        assertEquals(List.of("click 4"), game.actions);
        ticks(run, 10);

        game.screen = GuildScreen.MEMBERS;
        game.containerId = 2;
        game.items = List.of(head(3, "Steve", STEVE_ID));
        ticks(run, 1);
        game.stateId++; // the rest of the page arrives a tick later
        ticks(run, 4);
        assertTrue(presses().isEmpty());
        ticks(run, 3);

        assertEquals(List.of("click 4", "press 3 key 2"), game.actions);
    }

    @Test
    void pagesForwardUntilItFindsThePlayer() {
        List<MenuItem> page2 = List.of(item(10, "Previous Page"), head(2, "Steve", STEVE_ID));
        game.items = List.of(head(2, "Alex", null), item(28, "§a§lNext Page"));
        game.onClick = slot -> game.show(page2);
        RewardDistributor run = start(RewardKind.ASPECT, 1);

        ticks(run, 1);
        assertEquals(List.of("click 28"), game.actions);
        ticks(run, 5);
        assertTrue(presses().isEmpty());
        ticks(run, 2);

        assertEquals(List.of("click 28", "press 2 key 1"), game.actions);
    }

    @Test
    void searchesBackwardWhenStartedPastPageOne() {
        List<MenuItem> page1 = List.of(head(2, "Steve", STEVE_ID), item(28, "Next Page"));
        game.items = List.of(item(10, "Previous Page"), head(2, "Alex", null));
        game.onClick = slot -> game.show(page1);
        RewardDistributor run = start(RewardKind.TOME, 1);

        ticks(run, 8);

        assertEquals(List.of("click 10", "press 2 key 2"), game.actions);
    }

    @Test
    void givesUpWhenAPageDoesNotLoad() {
        game.items = List.of(head(2, "Alex", null), item(28, "Next Page"));
        RewardDistributor run = start(RewardKind.TOME, 1);

        ticks(run, 80);
        assertTrue(run.isRunning());
        ticks(run, 1);

        assertEquals("The member list didn't load.", result);
        assertEquals(Outcome.INTERRUPTED, outcome);
    }

    @Test
    void reportsAPlayerWhoIsNotOnTheList() {
        game.items = List.of(head(2, "Alex", null));
        RewardDistributor run = start(RewardKind.TOME, 1);

        ticks(run, 1);

        assertEquals("Steve isn't on the member list.", result);
        assertEquals(Outcome.PLAYER_SKIPPED, outcome);
        assertTrue(game.actions.isEmpty());
    }

    @Test
    void needsTheRewardKeyInTheLore() {
        game.items = List.of(new MenuItem(3, "Steve", List.of("Press 1 to send an Aspect"), STEVE_ID, "Steve"));
        RewardDistributor run = start(RewardKind.TOME, 2);

        ticks(run, 1);

        assertEquals("Your rank can't send tomes.", result);
        assertEquals(Outcome.REWARD_UNAVAILABLE, outcome);
        assertTrue(presses().isEmpty());
    }

    @Test
    void stopsWhenTheGuildRunsOut() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.ASPECT, 3);
        ticks(run, 2);
        run.onChat(guildLine("Chief rewarded an Aspect to Steve"));
        ticks(run, pace(RewardKind.ASPECT));

        run.onChat(Component.literal("Your guild does not have enough aspects to send a reward."));
        ticks(run, 1);

        assertEquals(2, presses().size());
        assertEquals("The guild ran out of aspects. Sent 1 of 3 aspects to Steve.", result);
        assertEquals(Outcome.REWARD_UNAVAILABLE, outcome);
    }

    @Test
    void stopsWithoutPermission() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.TOME, 2);
        ticks(run, 2);

        run.onChat(Component.literal("You do not have permission to send this reward."));
        ticks(run, 10);

        assertEquals(1, presses().size());
        assertEquals("You don't have permission to send tomes. Sent 0 of 2 tomes to Steve.", result);
        assertEquals(Outcome.REWARD_UNAVAILABLE, outcome);
    }

    @Test
    void pressesOnlyAsOftenAsTheAmountPerPressAllows() {
        game.items = List.of(new MenuItem(3, "Steve", List.of("Press 1 to send 2 Aspects"), STEVE_ID, "Steve"));
        RewardDistributor run = start(RewardKind.ASPECT, 5);
        ticks(run, 2);
        run.onChat(guildLine("Chief rewarded 2 Aspects to Steve"));
        ticks(run, pace(RewardKind.ASPECT));
        run.onChat(guildLine("Chief rewarded 2 Aspects to Steve"));
        ticks(run, 2);

        assertEquals(2, presses().size());
        assertEquals(List.of(2L, 2L), confirmed);
        assertEquals("Sent 4 of 5 aspects to Steve.", result);
        assertEquals(Outcome.PLAYER_SKIPPED, outcome);
    }

    @Test
    void refusesWhenOnePressSendsMoreThanIsOwed() {
        game.items = List.of(new MenuItem(3, "Steve", List.of("Press 1 to send 2 Aspects"), STEVE_ID, "Steve"));
        RewardDistributor run = start(RewardKind.ASPECT, 1);

        ticks(run, 1);

        assertEquals("Each press sends 2 Aspects, more than the 1 owed.", result);
        assertEquals(Outcome.PLAYER_SKIPPED, outcome);
        assertTrue(presses().isEmpty());
    }

    @Test
    void closingTheMenuBeforeThePressesFailsTheRun() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.TOME, 2);

        game.screen = null;
        ticks(run, 1);

        assertEquals("The menu was closed.", result);
        assertEquals(Outcome.INTERRUPTED, outcome);
        assertTrue(presses().isEmpty());
    }

    @Test
    void closingTheMenuStopsPressingButKeepsCounting() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.TOME, 2);
        ticks(run, 2);

        game.screen = null;
        ticks(run, 1);
        run.onChat(guildLine("Chief rewarded a Guild Tome to Steve"));
        ticks(run, 1);

        assertEquals(List.of(1L), confirmed);
        assertEquals("The menu was closed. Sent 1 of 2 tomes to Steve.", result);
        assertEquals(Outcome.INTERRUPTED, outcome);
    }

    @Test
    void aReopenedMemberListDoesNotStopTheRun() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.TOME, 2);
        ticks(run, 2);
        run.onChat(guildLine("Chief rewarded a Guild Tome to Steve"));

        game.containerId = 7; // Wynncraft opened the list again; its items come a moment later
        game.stateId = 1;
        game.items = List.of();
        ticks(run, pace(RewardKind.TOME));
        game.show(List.of(head(6, "Steve", STEVE_ID)));
        ticks(run, 7);

        assertEquals(List.of("press 3 key 2", "press 6 key 2"), presses());
        assertTrue(run.isRunning());
    }

    @Test
    void stopsAfterTwelvePageChanges() {
        game.items = List.of(head(2, "Alex", null), item(28, "Next Page"));
        game.onClick = slot -> game.show(game.items);
        RewardDistributor run = start(RewardKind.TOME, 1);

        for (int tick = 0; tick < 300 && run.isRunning(); tick++) {
            run.tick();
        }

        assertEquals("Stopped after 12 pages without finding Steve.", result);
        assertEquals(Outcome.PLAYER_SKIPPED, outcome);
        assertEquals(12, game.actions.size());
    }

    @Test
    void stoppingBeforeThePressesEndsTheRunAtOnce() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.TOME, 2);

        run.stop();

        assertFalse(run.isRunning());
        assertEquals("Stopped.", result);
        assertEquals(Outcome.INTERRUPTED, outcome);
        assertTrue(presses().isEmpty());
    }

    @Test
    void ignoresARefusalTypedInChat() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.TOME, 2);
        ticks(run, 2);
        run.onChat(guildLine("Chief rewarded a Guild Tome to Steve"));

        run.onChat(guildLine("Bob: your guild does not have enough tomes to send a reward"));
        ticks(run, pace(RewardKind.TOME));

        assertEquals(2, presses().size());
        assertTrue(run.isRunning());
    }

    @Test
    void aRefusalWhileFindingTheHeadAgainEndsTheRun() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.TOME, 2);
        ticks(run, 2);
        game.show(List.of(head(5, "Steve", STEVE_ID)));
        ticks(run, pace(RewardKind.TOME)); // the head moved: waiting for the menu before finding it again

        run.onChat(Component.literal("Your guild does not have enough tomes to send a reward."));
        ticks(run, 1);

        assertEquals(1, presses().size());
        assertEquals("The guild ran out of tomes. Sent 0 of 2 tomes to Steve.", result);
        assertEquals(Outcome.REWARD_UNAVAILABLE, outcome);
    }

    @Test
    void acceptsTheHeadsProfileNameWhenTheBotsNameIsOutdated() {
        game.items = List.of(head(3, "SteveRenamed", STEVE_ID));
        RewardDistributor run = start(RewardKind.TOME, 1);
        ticks(run, 2);

        run.onChat(guildLine("Chief rewarded a Guild Tome to SteveRenamed"));

        assertEquals(List.of(1L), confirmed);
    }

    @Test
    void countsOnlyItsOwnGrantsOfTheRightRewardToTheTarget() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.TOME, 1);
        run.onChat(guildLine("Chief rewarded a Guild Tome to Steve")); // before any press
        ticks(run, 2);

        run.onChat(guildLine("Other rewarded a Guild Tome to Steve"));
        run.onChat(guildLine("Chief rewarded a Guild Tome to Alex"));
        run.onChat(guildLine("Chief rewarded an Aspect to Steve"));
        run.onChat(Component.literal("Chief rewarded a Guild Tome to Steve")); // not guild chat
        run.onChat(guildLine("Chief: I rewarded a Guild Tome to Steve")); // typed in guild chat
        assertTrue(confirmed.isEmpty());

        run.onChat(guildLine("Chief rewarded a Guild Tome to Steve"));
        assertEquals(List.of(1L), confirmed);
    }

    @Test
    void sendsAllEmeraldsToTheFirstHeadOfPageOne() {
        List<MenuItem> page1 = List.of(head(2, "Krokofant", null), head(3, "Steve", STEVE_ID),
                item(28, "Next Page"));
        game.items = List.of(item(10, "Previous Page"), head(2, "Alex", null));
        game.onClick = slot -> game.show(page1);
        RewardDistributor run = RewardDistributor.forEmeralds(game, listener);
        run.start();

        ticks(run, 8);
        assertEquals(List.of("click 10", "press 2 key 3"), game.actions);
        run.onChat(guildLine("Chief rewarded 1,024 Emeralds to Krokofant"));
        assertEquals("Sending emeralds to Krokofant: 1,024", run.progress());
        ticks(run, pace(RewardKind.EMERALDS));
        run.onChat(guildLine("Chief rewarded 1,024 Emeralds to Krokofant"));
        ticks(run, pace(RewardKind.EMERALDS));
        run.onChat(Component.literal("Your guild does not have enough emeralds to send a reward."));
        ticks(run, 1);

        assertEquals(3, presses().size());
        assertEquals("Sent 2,048 emeralds to Krokofant.", result);
        assertEquals(Outcome.DONE, outcome);
    }

    @Test
    void theEmeraldRunKeepsPressingUntilStorageIsEmpty() {
        game.items = List.of(head(2, "Krokofant", null));
        RewardDistributor run = RewardDistributor.forEmeralds(game, listener);
        run.start();

        ticks(run, 100);
        assertTrue(run.isRunning());
        int sent = presses().size();
        run.onChat(Component.literal("Your guild does not have enough emeralds to send a reward."));
        ticks(run, RewardDistributor.LATE_CONFIRMATION_TICKS);

        assertFalse(run.isRunning());
        assertEquals(sent, presses().size());
        assertEquals("Sent 0 emeralds to Krokofant.", result);
        assertEquals(Outcome.DONE, outcome);
    }

    @Test
    void abortEndsTheRunAtOnce() {
        game.items = List.of(head(3, "Steve", STEVE_ID));
        RewardDistributor run = start(RewardKind.TOME, 2);
        ticks(run, 2);
        run.onChat(guildLine("Chief rewarded a Guild Tome to Steve"));

        run.abort("Disconnected.");

        assertFalse(run.isRunning());
        assertEquals("Disconnected. Sent 1 of 2 tomes to Steve.", result);
        assertEquals(Outcome.INTERRUPTED, outcome);
    }
}
