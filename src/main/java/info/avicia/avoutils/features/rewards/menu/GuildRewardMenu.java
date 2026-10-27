package info.avicia.avoutils.features.rewards.menu;

import info.avicia.avoutils.core.util.PacketTextNormalizer;
import info.avicia.avoutils.features.rewards.RewardKind;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Wynncraft's guild menus: which one is open, the members' heads, their reward keys and the page buttons.
 * Works on plain {@link MenuItem}s, so it's tested without a game.
 */
public final class GuildRewardMenu {
    static final String GUILD = "Avicia";

    private static final Pattern REWARD_ACTION = Pattern.compile(
            "(?i)^press\\s+([1-9])\\s+to\\s+send\\s+([\\d, ]+|an?|one)\\s+(.+?)$");
    private static final String GUILD_ENTRY_PREFIX = GUILD.toLowerCase(Locale.ROOT) + " [lv.";

    public enum GuildScreen { MANAGE, MEMBERS }

    /**
     * An item in the open menu, with its raw texts. {@code profileId} and {@code profileName} come from a player
     * head's profile and are null when it has none.
     */
    public record MenuItem(int slot, String name, List<String> lore, UUID profileId, String profileName) {}

    /** Pressing number key {@code key} on the head sends {@code amountPerPress}. */
    public record RewardAction(int key, long amountPerPress) {}

    private GuildRewardMenu() {
    }

    /** Which guild menu a container title belongs to, or null for any other container. */
    public static GuildScreen screenOf(String title) {
        String normalized = PacketTextNormalizer.normalizeForParsing(title);
        if (normalized.equalsIgnoreCase(GUILD + ": Manage")) return GuildScreen.MANAGE;
        if (normalized.equalsIgnoreCase(GUILD + ": Members")) return GuildScreen.MEMBERS;
        return null;
    }

    /** The member list's heads are in rows 0-4, columns 2-8. */
    static boolean isMemberSlot(int slot) {
        return slot >= 0 && slot < 45 && slot % 9 >= 2;
    }

    /** The key that sends {@code kind} and the amount each press sends, from a head's lore; null if it has none. */
    public static RewardAction rewardAction(List<String> lore, RewardKind kind) {
        for (String line : lore) {
            Matcher matcher = REWARD_ACTION.matcher(PacketTextNormalizer.normalizeForParsing(line));
            if (!matcher.matches() || !isRewardLabel(matcher.group(3), kind)) continue;
            long amount = parseAmount(matcher.group(2));
            if (amount > 0) {
                return new RewardAction(Integer.parseInt(matcher.group(1)), amount);
            }
        }
        return null;
    }

    private static boolean isRewardLabel(String label, RewardKind kind) {
        String lower = label.toLowerCase(Locale.ROOT);
        return switch (kind) {
            case EMERALDS -> lower.startsWith("emerald");
            case ASPECT -> lower.startsWith("aspect");
            case TOME -> lower.contains("tome");
            case LE -> false;
        };
    }

    /** Digits with "," or ", " separators (the normalizer turns "1,024" into "1, 024"), or a, an, one. */
    private static long parseAmount(String raw) {
        String amount = raw.trim().toLowerCase(Locale.ROOT);
        if (amount.equals("a") || amount.equals("an") || amount.equals("one")) return 1;
        try {
            return Long.parseLong(amount.replace(",", "").replace(" ", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * The member's head on this page: the one whose profile id is {@code uuid}, else whose profile name is
     * {@code username}, else whose name shows {@code username}.
     * A different profile id doesn't rule out a name match, because the game makes one up when the server sends none.
     */
    public static MenuItem findMember(List<MenuItem> items, UUID uuid, String username) {
        List<MenuItem> heads = items.stream().filter(item -> isMemberSlot(item.slot())).toList();
        if (uuid != null) {
            for (MenuItem head : heads) {
                if (uuid.equals(head.profileId())) return head;
            }
        }
        if (username == null) return null;
        for (MenuItem head : heads) {
            if (username.equalsIgnoreCase(head.profileName())) return head;
        }
        for (MenuItem head : heads) {
            if (containsName(head.name(), username)) return head;
        }
        return null;
    }

    /** Whether {@code username} shows in {@code text} as a whole name, not as part of a longer one. */
    public static boolean containsName(String text, String username) {
        if (text == null || username == null || username.isEmpty()) return false;
        Pattern name = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(username) + "(?![A-Za-z0-9_])",
                Pattern.CASE_INSENSITIVE);
        return name.matcher(PacketTextNormalizer.normalizeForParsing(text)).find();
    }

    /** The first head of the member list's page; on page 1 that's the owner. */
    public static MenuItem firstMember(List<MenuItem> items) {
        MenuItem first = null;
        for (MenuItem item : items) {
            if (isMemberSlot(item.slot()) && (first == null || item.slot() < first.slot())) first = item;
        }
        return first;
    }

    /** The Manage menu's guild item ("Avicia [Lv. …]"), which opens the member list. */
    public static MenuItem findGuildEntry(List<MenuItem> items) {
        for (MenuItem item : items) {
            String name = PacketTextNormalizer.normalizeForParsing(item.name()).toLowerCase(Locale.ROOT);
            if (name.startsWith(GUILD_ENTRY_PREFIX)) return item;
        }
        return null;
    }

    public static MenuItem findNextPage(List<MenuItem> items) {
        return findByName(items, "next page");
    }

    /** Null on the first page. */
    public static MenuItem findPreviousPage(List<MenuItem> items) {
        return findByName(items, "previous page");
    }

    private static MenuItem findByName(List<MenuItem> items, String name) {
        for (MenuItem item : items) {
            if (PacketTextNormalizer.normalizeForParsing(item.name()).toLowerCase(Locale.ROOT).contains(name)) {
                return item;
            }
        }
        return null;
    }
}
