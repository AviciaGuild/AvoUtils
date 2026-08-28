package info.avicia.avoutils.features.anniparty;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.party.InGamePartyTracker;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;

import java.util.List;
import java.util.function.Consumer;

/**
 * Anni-specific side effects for in-game party tracking. Sends {@code mod_party_update}
 * with the detected in-game party when the local player leads an Anni party.
 */
public class AnniPartySyncer {

    private static final String EVT_MOD_PARTY_UPDATE = "mod_party_update";

    private final Consumer<List<String>> partyListListener = this::onPartyListParsed;

    public AnniPartySyncer() {
        InGamePartyTracker.getInstance().addPartyListListener(partyListListener);
    }

    private void onPartyListParsed(List<String> members) {
        AnniPartyFeature feature = AvoUtilsMod.getInstance().getFeature(AnniPartyFeature.class);
        if (feature == null) {
            return;
        }
        AnniPartyData ledParty = feature.getLedParty();
        if (ledParty == null) {
            return;
        }
        sendPartyUpdate(ledParty, members);
    }

    private void sendPartyUpdate(AnniPartyData ledParty, List<String> members) {
        String selfName = PlayerUtil.selfName();
        if (selfName == null) {
            return;
        }

        JsonArray inParty = new JsonArray();
        inParty.add(selfName);
        for (String member : members) {
            if (!member.equalsIgnoreCase(selfName)) {
                inParty.add(member);
            }
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("party_id", ledParty.partyId);
        payload.add("in_party", inParty);

        AvoWebSocketManager.getInstance().sendEvent(EVT_MOD_PARTY_UPDATE, payload);
    }
}
