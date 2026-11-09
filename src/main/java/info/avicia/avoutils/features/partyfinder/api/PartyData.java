package info.avicia.avoutils.features.partyfinder.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import info.avicia.avoutils.features.partyfinder.RoleIconUtil;

public class PartyData {
    public long partyId;
    public String leaderName;
    public List<String> activities;
    public String region;
    public String note;
    public int memberCount;
    public int maxSize;
    public boolean isFull;
    public boolean ping = true;
    public Map<String, MemberData> members;

    /**
     * Slash-joined activity label (e.g. {@code "NOTG / TNA"}), or empty string when absent.
     */
    public String activitiesLabel() {
        return activities != null ? String.join(" / ", activities) : "";
    }

    /**
     * Non-null member names in display order.
     */
    public List<String> memberNames() {
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.values().stream()
                .filter(Objects::nonNull)
                .map(m -> m.name)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Update this party snapshot in-place from a freshly fetched {@link PartyData}.
     */
    public void copyFrom(PartyData other) {
        if (other == null) {
            return;
        }
        this.partyId = other.partyId;
        this.leaderName = other.leaderName;
        this.activities = other.activities;
        this.region = other.region;
        this.note = other.note;
        this.memberCount = other.memberCount;
        this.maxSize = other.maxSize;
        this.isFull = other.isFull;
        this.ping = other.ping;
        if (this.members == null) {
            this.members = new LinkedHashMap<>();
        } else {
            this.members.clear();
        }
        if (other.members != null) {
            this.members.putAll(other.members);
        }
    }

    public static class MemberData {
        public String name;
        public String role;
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
