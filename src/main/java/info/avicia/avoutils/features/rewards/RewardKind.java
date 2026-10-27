package info.avicia.avoutils.features.rewards;

import java.util.Locale;

/** The rewards the panel shows, plus emeralds, which only the "all emeralds → owner" button sends. */
public enum RewardKind {
    LE("le", "LE", "LE", false),
    TOME("tome", "Tome", "Tomes", true),
    ASPECT("aspect", "Aspect", "Aspects", true),
    /** Not a bot reward: the emerald button's run only. */
    EMERALDS(null, "Emerald", "Emeralds", true);

    private final String apiName;
    private final String singular;
    private final String plural;
    private final boolean handedOutByMod;

    RewardKind(String apiName, String singular, String plural, boolean handedOutByMod) {
        this.apiName = apiName;
        this.singular = singular;
        this.plural = plural;
        this.handedOutByMod = handedOutByMod;
    }

    /** The reward type in the bot's API. */
    public String apiName() {
        return apiName;
    }

    /** Whether the mod can hand it out by pressing the reward key; LE is traded and recorded by hand. */
    public boolean handedOutByMod() {
        return handedOutByMod;
    }

    /** "2 Tomes", "1 Tome", "11 LE". */
    public String label(long amount) {
        return amount + " " + (amount == 1 ? singular : plural);
    }

    /** "tomes", "aspects", "LE", for sentences. */
    public String plural() {
        return this == LE ? plural : plural.toLowerCase(Locale.ROOT);
    }

    /** The bot reward with this API name, or null if the mod doesn't know it. */
    public static RewardKind fromApiName(String apiName) {
        for (RewardKind kind : values()) {
            if (kind.apiName != null && kind.apiName.equals(apiName)) {
                return kind;
            }
        }
        return null;
    }
}
