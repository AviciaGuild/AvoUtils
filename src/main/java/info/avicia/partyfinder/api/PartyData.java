package info.avicia.partyfinder.api;

import java.util.List;
import java.util.Map;

public class PartyData {
    public long partyId;
    public long creatorId;
    public String leaderName;
    public String leaderRole;
    public List<String> activities;
    public String region;
    public String note;
    public int memberCount;
    public int maxSize;
    public boolean isFull;
    public Map<String, MemberData> members;

    public static class MemberData {
        public String name;
        public String role;
        public Long userId;
        public boolean isReserved;
        public String guildTag;

        /**
         * Get a display string for this member (e.g. "PlayerName [TAG]").
         */
        public String displayName() {
            if (guildTag != null && !guildTag.isEmpty()) {
                return name + " [" + guildTag + "]";
            }
            return name;
        }

        /**
         * Get the role icon character for this member.
         */
        public String roleIcon() {
            if (isReserved && role == null) {
                return "🔒";
            }
            if (role == null) return "🧩";
            return switch (role) {
                case "dps" -> "⚔️";
                case "healer" -> "❤️";
                case "tank" -> "🛡️";
                case "other" -> "🧩";
                default -> "🧩";
            };
        }
    }
}
