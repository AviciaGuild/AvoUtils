package info.avicia.avoutils.features.partyfinder.api;

import java.util.List;
import java.util.Map;

import info.avicia.avoutils.features.partyfinder.RoleIconUtil;

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
    public boolean ping = true;
    public Map<String, MemberData> members;

    public static class MemberData {
        public String name;
        public String role;
        public Long userId;
        public boolean isReserved;
        public String guildTag;

        /**
         * Get a display string for this member (e.g. "PlayerName [TAG]")
         */
        public String displayName() {
            String baseName = name != null ? name : "<RESERVED>";
            if (guildTag != null && !guildTag.isEmpty()) {
                return baseName + " §7[" + guildTag + "]";
            }
            return baseName;
        }

        /**
         * Get the role icon character for this member
         */
        public String getStyledRolePrefix() {
            if (isReserved && role == null) {
                return "§7\uD83D\uDD12"; // 🔒
            }
            return RoleIconUtil.getStyledRolePrefix(role);
        }
    }
}
