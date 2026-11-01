package info.avicia.avoutils.features.rewards.api;

import java.util.Map;

/** One player in {@code GET /api/rewards/pending}: what they're owed per reward type. */
public class PlayerRewards {
    public String uuid;
    public String username;
    public boolean inGuild;
    public Map<String, Long> rewards;
}
