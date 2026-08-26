package info.avicia.avoutils.features.anniparty;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Registers the Anni party roster feature: the WebSocket listener that keeps the
 * local cached roster fresh, the chat detector that auto-syncs leader party state,
 * and the {@code /avo anni} command that opens {@link AnniPartyScreen}.
 */
public class AnniPartyFeature implements AvoFeature {

    private static final String EVT_ANNI_ROSTER_SYNC = "anni_roster_sync";

    private final Gson gson = new Gson();
    private AnniPartyDetector detector;
    private AnniRoster roster = new AnniRoster();
    private boolean active = false;

    @Override
    public void initialize(ModConfig config) {
        detector = new AnniPartyDetector();

        // Keep the cached roster live
        AvoWebSocketManager.getInstance().registerListener(EVT_ANNI_ROSTER_SYNC, json -> onRosterEvent(json));

        // Periodically refresh /party list so in-party checkboxes stay accurate
        // even when a join/leave chat event is missed.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (detector != null) {
                detector.onClientTick();
            }
        });

        // AvoWebSocketManager is already kept connected by partyfinder's demand;
        // no extra connection demand is needed.
    }

    private void onRosterEvent(JsonObject json) {
        AnniRoster parsed = gson.fromJson(json, AnniRoster.class);
        if (parsed == null) {
            return;
        }
        this.roster = parsed;
        this.active = parsed.active;
        if (detector != null) {
            detector.onRosterUpdated(parsed);
        }
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

    public AnniPartyDetector getDetector() {
        return detector;
    }

    /**
     * The party the local player leads within the current roster, or null.
     */
    public AnniPartyData getLedParty() {
        if (!active) {
            return null;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null) {
            return null;
        }
        return roster.findLedParty(mc.getSession().getUsername());
    }
}
