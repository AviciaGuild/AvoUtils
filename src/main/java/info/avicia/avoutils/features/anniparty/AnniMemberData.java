package info.avicia.avoutils.features.anniparty;

/**
 * A single Anni party member as received from the backend {@code anni_roster_sync} event.
 */
public class AnniMemberData {
    public String name;
    public String role;
    public boolean is_leader;
    public boolean in_party;

    public String getDisplayName() {
        return name != null ? name : "?";
    }
}
