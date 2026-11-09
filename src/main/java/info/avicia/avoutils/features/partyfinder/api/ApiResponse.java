package info.avicia.avoutils.features.partyfinder.api;

import java.util.List;

/**
 * Wrapper for API JSON responses
 */
public class ApiResponse {
    public boolean ok;
    public String error;
    public String message;
    public PartyData party;
    public List<PartyData> parties;
    public ApiData data;

    public static class ApiData {
        public Long partyId;
    }
}
