package info.avicia.avoutils.features.anniparty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single Anni party as received from the backend {@code anni_roster_sync} event.
 */
public class AnniPartyData {
    public int party_id;
    public String server;
    public List<AnniMemberData> members = new ArrayList<>();

    public List<AnniMemberData> getMembers() {
        return members != null ? members : Collections.emptyList();
    }

    public String getServerDisplay() {
        return (server == null || server.isBlank()) ? "???" : server;
    }
}
