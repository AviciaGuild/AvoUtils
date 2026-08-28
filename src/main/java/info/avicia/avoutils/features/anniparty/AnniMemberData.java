package info.avicia.avoutils.features.anniparty;

import com.google.gson.annotations.SerializedName;

/**
 * A single Anni party member as received from the backend {@code anni_roster_sync} event.
 */
public class AnniMemberData {
    public String name;
    public String role;

    @SerializedName("is_leader")
    public boolean isLeader;

    @SerializedName("in_party")
    public boolean inParty;

    public String getDisplayName() {
        return name != null ? name : "?";
    }
}
