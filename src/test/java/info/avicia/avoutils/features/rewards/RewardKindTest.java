package info.avicia.avoutils.features.rewards;

import info.avicia.avoutils.features.rewards.RewardsFeature.PlayerEntry;
import info.avicia.avoutils.features.rewards.api.PlayerRewards;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardKindTest {

    @Test
    void builtInKindsKeepSingletonsAndHandOutFlags() {
        assertSame(RewardKind.LE, RewardKind.fromApiName("le"));
        assertSame(RewardKind.TOME, RewardKind.fromApiName("tome"));
        assertSame(RewardKind.ASPECT, RewardKind.fromApiName("aspect"));

        assertFalse(RewardKind.LE.isCustom());
        assertFalse(RewardKind.TOME.isCustom());
        assertFalse(RewardKind.ASPECT.isCustom());
        assertFalse(RewardKind.EMERALDS.isCustom());

        assertFalse(RewardKind.LE.handedOutByMod());
        assertTrue(RewardKind.TOME.handedOutByMod());
        assertTrue(RewardKind.ASPECT.handedOutByMod());
        assertTrue(RewardKind.EMERALDS.handedOutByMod());
    }

    @Test
    void freeformKindsPreserveExactApiNameAndEquality() {
        RewardKind first = RewardKind.fromApiName("Shiny Warp");
        RewardKind second = RewardKind.fromApiName("Shiny Warp");

        assertTrue(first.isCustom());
        assertFalse(first.handedOutByMod());
        assertEquals("Shiny Warp", first.apiName());
        assertEquals("Shiny Warp", first.displayName());
        assertEquals("2× Shiny Warp", first.label(2));
        assertEquals("1× Shiny Warp", first.label(1));
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, RewardKind.LE);
        assertNotEquals(first, RewardKind.EMERALDS);

        assertNull(RewardKind.fromApiName(null));
        assertNull(RewardKind.fromApiName(""));
        assertNull(RewardKind.fromApiName("   "));
    }

    @Test
    void toEntriesIncludesCustomRewardsOrderedAfterColumns() {
        PlayerRewards mixed = new PlayerRewards();
        mixed.uuid = "u1";
        mixed.username = "Steve";
        mixed.inGuild = true;
        mixed.rewards = new LinkedHashMap<>();
        mixed.rewards.put("Zombie Eye", 3L);
        mixed.rewards.put("aspect", 6L);
        mixed.rewards.put("amplified Dungeon Key", 1L);
        mixed.rewards.put("le", 10L);
        mixed.rewards.put("Shiny Warp", 2L);
        mixed.rewards.put("   ", 5L);

        PlayerRewards customOnly = new PlayerRewards();
        customOnly.uuid = "u2";
        customOnly.username = "Alex";
        customOnly.inGuild = true;
        customOnly.rewards = new LinkedHashMap<>();
        customOnly.rewards.put("Mythic Relik", 1L);

        List<PlayerEntry> entries = RewardsFeature.toEntries(List.of(mixed, customOnly));
        assertEquals(2, entries.size());

        PlayerEntry steve = entries.get(0);
        assertEquals(
                List.of("le", "aspect", "amplified Dungeon Key", "Shiny Warp", "Zombie Eye"),
                new ArrayList<>(steve.rewards().keySet()).stream().map(RewardKind::apiName).toList());
        assertEquals(2L, steve.rewards().get(RewardKind.fromApiName("Shiny Warp")));

        PlayerEntry alex = entries.get(1);
        assertEquals(List.of(RewardKind.fromApiName("Mythic Relik")), new ArrayList<>(alex.rewards().keySet()));
        assertEquals(1L, alex.rewards().get(RewardKind.fromApiName("Mythic Relik")));
    }
}

