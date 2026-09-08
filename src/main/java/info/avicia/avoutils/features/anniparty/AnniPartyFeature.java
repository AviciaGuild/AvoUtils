package info.avicia.avoutils.features.anniparty;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Registers the Anni party roster feature: the WebSocket listener that keeps the local cached
 * roster fresh, and the syncer that reports the leader's in-game party back to the backend.
 */
public class AnniPartyFeature implements AvoFeature {

    private static final String EVT_ANNI_ROSTER_SYNC = "anni_roster_sync";

    private final Gson gson = new Gson();
    private final List<Consumer<AnniRoster>> rosterListeners = new CopyOnWriteArrayList<>();

    private AnniPartySyncer syncer;
    private AnniRoster roster = new AnniRoster();
    private boolean active = false;

    @Override
    public void initialize(ModConfig config) {
        syncer = new AnniPartySyncer();

        AvoWebSocketManager.getInstance().registerListener(EVT_ANNI_ROSTER_SYNC, this::onRosterEvent);

        // Register a connection demand so that the backend will send us the roster when we connect
        AvoWebSocketManager.getInstance().registerConnectionDemand("anniparty", this::isGuildMember);
    }

    private void onRosterEvent(JsonObject json) {
        if (!isGuildMember()) {
            return;
        }
        AnniRoster parsed = gson.fromJson(json, AnniRoster.class);
        if (parsed == null) {
            return;
        }
        this.roster = parsed;
        this.active = parsed.active;
        notifyRosterListeners(parsed);
    }

    /**
     * Current cached roster (may be inactive).
     */
    public AnniRoster getRoster() {
        return roster;
    }

    public boolean isActive() {
        return active;
    }

    private boolean isGuildMember() {
        return AvoAuthService.getInstance().isGuildMember();
    }

    /**
     * The party the local player leads within the current roster, or null.
     */
    public AnniPartyData getLedParty() {
        if (!active) {
            return null;
        }
        String selfName = PlayerUtil.selfName();
        if (selfName == null) {
            return null;
        }
        return roster.findLedParty(selfName);
    }

    public void addRosterListener(Consumer<AnniRoster> listener) {
        rosterListeners.add(listener);
    }

    public void removeRosterListener(Consumer<AnniRoster> listener) {
        rosterListeners.remove(listener);
    }

    private void notifyRosterListeners(AnniRoster updatedRoster) {
        for (Consumer<AnniRoster> listener : rosterListeners) {
            try {
                listener.accept(updatedRoster);
            } catch (Exception e) {
                AvoUtilsMod.LOGGER.error("[AvoUtils] [AnniParty] Roster listener error", e);
            }
        }
    }
}
