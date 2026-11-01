package info.avicia.avoutils.features.rewards.api;

import java.util.List;

/** Any reply of the rewards API; each route fills its own fields. {@code ok} is false for a 409. */
public class RewardsResponse {
    public boolean ok;
    public String error;
    public List<PlayerRewards> players;
    public Long claimed;
    public Long pending;
    public String lockedBy;
}
