package info.avicia.avoutils.features.rewards;

import java.util.Locale;
import java.util.Objects;

/**
 * A reward type in the pending-rewards panel: one of the built-in types ({@link #LE}, {@link #TOME}, {@link #ASPECT}),
 * a freeform item name from the bot's ledger (e.g. {@code "Shiny Warp"}), or {@link #EMERALDS} for the owner emerald
 * sweep.
 */
public final class RewardKind {
    public static final RewardKind LE = new RewardKind("le", "LE", "LE", "LE", false);
    public static final RewardKind TOME = new RewardKind("tome", "Tome", "Tomes", "TOME", true);
    public static final RewardKind ASPECT = new RewardKind("aspect", "Aspect", "Aspects", "ASPECT", true);
    /** Not a bot reward: the emerald button's run only. */
    public static final RewardKind EMERALDS = new RewardKind(null, "Emerald", "Emeralds", "EMERALDS", true);

    private final String apiName;
    private final String singular;
    private final String plural;
    private final String debugName;
    private final boolean handedOutByMod;

    private RewardKind(String apiName, String singular, String plural, String debugName, boolean handedOutByMod) {
        this.apiName = apiName;
        this.singular = singular;
        this.plural = plural;
        this.debugName = debugName;
        this.handedOutByMod = handedOutByMod;
    }

    /** The reward type in the bot's API. */
    public String apiName() {
        return apiName;
    }

    /** Display name for custom/freeform rewards (and singular name for built-in ones). */
    public String displayName() {
        return singular;
    }

    /** True for freeform item rewards outside the LE / Tome / Aspect columns. */
    public boolean isCustom() {
        return this != LE && this != TOME && this != ASPECT && this != EMERALDS;
    }

    /** Whether the mod can hand it out by pressing the reward key; LE and custom items are traded and recorded by hand. */
    public boolean handedOutByMod() {
        return handedOutByMod;
    }

    /** "2 Tomes", "1 Tome", "11 LE", "2× Shiny Warp". */
    public String label(long amount) {
        if (isCustom()) {
            return amount + "× " + singular;
        }
        return amount + " " + (amount == 1 ? singular : plural);
    }

    /** "tomes", "aspects", "LE", or the custom item's name, for sentences. */
    public String plural() {
        return noun(2);
    }

    /** "tome" or "tomes", for {@code amount} in a sentence. */
    public String noun(long amount) {
        String noun = amount == 1 ? singular : plural;
        return (this == LE || isCustom()) ? noun : noun.toLowerCase(Locale.ROOT);
    }

    /** The bot reward with this API name (built-in or freeform), or null if blank. */
    public static RewardKind fromApiName(String apiName) {
        if (apiName == null || apiName.isBlank()) {
            return null;
        }
        return switch (apiName) {
            case "le" -> LE;
            case "tome" -> TOME;
            case "aspect" -> ASPECT;
            default -> new RewardKind(apiName, apiName, apiName, apiName, false);
        };
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RewardKind other)) return false;
        if (apiName == null || other.apiName == null) return false;
        return apiName.equals(other.apiName);
    }

    @Override
    public int hashCode() {
        return apiName != null ? Objects.hashCode(apiName) : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return debugName;
    }
}
