package info.avicia.avoutils.features.anniparty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The full Anni party roster as received from the backend {@code anni_roster_sync} event.
 */
public class AnniRoster {
    public boolean active;
    public List<AnniPartyData> parties = new ArrayList<>();

    public List<AnniPartyData> getParties() {
        return parties != null ? parties : Collections.emptyList();
    }

    /**
     * Find the party that contains the given in-game name, or null if not present.
     */
    public AnniPartyData findPartyContaining(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        for (AnniPartyData party : getParties()) {
            for (AnniMemberData member : party.getMembers()) {
                if (member.name != null && member.name.toLowerCase(Locale.ROOT).equals(lower)) {
                    return party;
                }
            }
        }
        return null;
    }

    /**
     * Find the party the given player is the leader of, or null if not present.
     */
    public AnniPartyData findLedParty(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        for (AnniPartyData party : getParties()) {
            for (AnniMemberData member : party.getMembers()) {
                if (member.isLeader
                        && member.name != null
                        && member.name.toLowerCase(Locale.ROOT).equals(lower)) {
                    return party;
                }
            }
        }
        return null;
    }
}
