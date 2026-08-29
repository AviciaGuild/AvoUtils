package info.avicia.avoutils.features.anniparty;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single Anni party as received from the backend {@code anni_roster_sync} event.
 */
public class AnniPartyData {
    @SerializedName("party_id")
    public long partyId;

    public String server;
    public List<AnniMemberData> members = new ArrayList<>();

    public List<AnniMemberData> getMembers() {
        return members != null ? members : Collections.emptyList();
    }

    public String getServerDisplay() {
        return (server == null || server.isBlank()) ? "???" : server;
    }
}
