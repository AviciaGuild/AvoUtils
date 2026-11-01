package info.avicia.avoutils.features.rewards.menu;

import info.avicia.avoutils.features.rewards.RewardKind;
import info.avicia.avoutils.features.rewards.menu.GuildRewardMenu.GuildScreen;
import info.avicia.avoutils.features.rewards.menu.GuildRewardMenu.MenuItem;
import info.avicia.avoutils.features.rewards.menu.GuildRewardMenu.RewardAction;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildRewardMenuTest {

    private static final UUID STEVE = UUID.fromString("0f1e2d3c-4b5a-4978-8796-a5b4c3d2e1f0");

    private static MenuItem item(int slot, String name) {
        return new MenuItem(slot, name, List.of(), null, null);
    }

    private static MenuItem head(int slot, String name, UUID id, String profileName) {
        return new MenuItem(slot, name, List.of(), id, profileName);
    }

    @Test
    void recognizesTheGuildScreensByTitle() {
        assertEquals(GuildScreen.MANAGE, GuildRewardMenu.screenOf("Avicia: Manage"));
        assertEquals(GuildScreen.MEMBERS, GuildRewardMenu.screenOf("§f\uE000 Avicia: Members"));
        assertNull(GuildRewardMenu.screenOf("Avicia: Bank"));
        assertNull(GuildRewardMenu.screenOf("Otherguild: Members"));
        assertNull(GuildRewardMenu.screenOf(null));
    }

    @Test
    void readsTheKeyAndAmountOfEachReward() {
        List<String> lore = List.of("§7Rank: Chief", "§7Press 2 to send a Guild Tome", "Press 1 to send one Aspect",
                "Press 3 to send 1,024 Emeralds");

        assertEquals(new RewardAction(2, 1), GuildRewardMenu.rewardAction(lore, RewardKind.TOME));
        assertEquals(new RewardAction(1, 1), GuildRewardMenu.rewardAction(lore, RewardKind.ASPECT));
        assertEquals(new RewardAction(3, 1024), GuildRewardMenu.rewardAction(lore, RewardKind.EMERALDS));
        assertNull(GuildRewardMenu.rewardAction(lore, RewardKind.LE));
    }

    @Test
    void readsAmountsWrittenAsDigitsOrWords() {
        assertEquals(new RewardAction(4, 2), GuildRewardMenu.rewardAction(List.of("Press 4 to send 2 Aspects"),
                RewardKind.ASPECT));
        assertEquals(new RewardAction(9, 1), GuildRewardMenu.rewardAction(List.of("Press 9 to send an Aspect"),
                RewardKind.ASPECT));
        assertEquals(new RewardAction(5, 1024), GuildRewardMenu.rewardAction(List.of("Press 5 to send 1, 024 Emeralds"),
                RewardKind.EMERALDS));
    }

    @Test
    void ignoresLinesWithoutAUsableKeyOrAmount() {
        assertNull(GuildRewardMenu.rewardAction(List.of("Press 0 to send a Guild Tome"), RewardKind.TOME));
        assertNull(GuildRewardMenu.rewardAction(List.of("Press 10 to send a Guild Tome"), RewardKind.TOME));
        assertNull(GuildRewardMenu.rewardAction(List.of("Press 1 to send 0 Aspects"), RewardKind.ASPECT));
        assertNull(GuildRewardMenu.rewardAction(List.of("Press 1 to send a Guild Tome"), RewardKind.ASPECT));
        assertNull(GuildRewardMenu.rewardAction(List.of(), RewardKind.TOME));
    }

    @Test
    void findsAMemberByProfileIdFirst() {
        List<MenuItem> items = List.of(head(2, "Steve", null, "Steve"), head(3, "SteveNew", STEVE, "SteveNew"));

        assertEquals(3, GuildRewardMenu.findMember(items, STEVE, "Steve").slot());
    }

    @Test
    void findsAMemberByProfileNameThenByWholeNameInTheItemName() {
        assertEquals(4, GuildRewardMenu.findMember(List.of(head(2, "Steve2", null, "Steve2"),
                head(4, "§bsomething", UUID.randomUUID(), "steve")), STEVE, "Steve").slot());
        assertEquals(5, GuildRewardMenu.findMember(List.of(head(2, "Steve2", null, null),
                head(5, "§6★ §bSteve", null, null)), STEVE, "Steve").slot());
    }

    @Test
    void doesNotTakeALongerNameForTheMember() {
        List<MenuItem> items = List.of(head(2, "Steve2", null, "Steve2"), head(3, "xSteve", null, null));

        assertNull(GuildRewardMenu.findMember(items, STEVE, "Steve"));
    }

    @Test
    void onlySearchesTheMemberSlots() {
        List<MenuItem> items = List.of(head(0, "Steve", STEVE, "Steve"), head(10, "Steve", STEVE, "Steve"),
                head(45, "Steve", STEVE, "Steve"));

        assertNull(GuildRewardMenu.findMember(items, STEVE, "Steve"));
        assertNull(GuildRewardMenu.firstMember(items));
    }

    @Test
    void theFirstMemberIsTheFirstHeadInReadingOrder() {
        List<MenuItem> items = List.of(item(28, "Next Page"), head(11, "Second", null, null),
                head(4, "Owner", null, null), head(1, "Not a member slot", null, null));

        assertEquals("Owner", GuildRewardMenu.firstMember(items).name());
    }

    @Test
    void findsTheGuildEntryAndThePageItemsByName() {
        List<MenuItem> items = List.of(item(4, "§bAvicia §7[Lv. 120]"), item(10, "§a§lPrevious Page"),
                item(28, "§a§lNext Page"));

        assertEquals(4, GuildRewardMenu.findGuildEntry(items).slot());
        assertEquals(28, GuildRewardMenu.findNextPage(items).slot());
        assertEquals(10, GuildRewardMenu.findPreviousPage(items).slot());
        assertNull(GuildRewardMenu.findPreviousPage(List.of(item(10, "Guild Bank"))));
        assertNull(GuildRewardMenu.findGuildEntry(List.of(item(4, "Otherguild [Lv. 120]"))));
    }

    @Test
    void matchesWholeNamesOnly() {
        assertTrue(GuildRewardMenu.containsName("[Chief] Steve", "steve"));
        assertTrue(GuildRewardMenu.containsName("§bSteve§7's head", "Steve"));
        assertFalse(GuildRewardMenu.containsName("Steve2", "Steve"));
        assertFalse(GuildRewardMenu.containsName("Big_Steve", "Steve"));
        assertFalse(GuildRewardMenu.containsName(null, "Steve"));
    }
}
