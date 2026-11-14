package info.avicia.avoutils.features.wardetector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.util.PacketTextNormalizer;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.core.util.WynncraftServerPolicy.Scope;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;

/**
 * Feature that detects guild war outcomes and participants, relaying results to the Avicia gateway.
 */
public class WarDetectorFeature implements AvoFeature {

    private static final String EVT_GUILD_WAR = "guild_war_result";

    private final WarDetector detector;

    public WarDetectorFeature() {
        this(new WarDetector());
    }

    WarDetectorFeature(WarDetector detector) {
        this.detector = detector;
    }

    public boolean isGuildMember() {
        return AvoAuthService.getInstance().isGuildMember();
    }

    @Override
    public void initialize(ModConfig config) {
        // Continuous client tick for war tracking while on Wynncraft
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (isGuildMember() && WynncraftServerPolicy.isOnWynncraft()) {
                detector.tick();
            }
        });

        // Reset war tracking state on disconnect
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> detector.reset());

        // Register connection demand lease for verified guild members
        AvoWebSocketManager.getInstance().registerConnectionDemand("wardetector", this::isGuildMember);

        AvoUtilsMod.LOGGER.info("[AvoUtils] [WarDetector] Initialized.");
    }

    /**
     * Called when a system chat packet is received.
     */
    public void onSystemChat(Component message) {
        if (message == null || !detector.isTrackingWar() || !isGuildMember()) return;
        if (!AvoWebSocketManager.getInstance().isConnected()) return;

        String cleaned = PacketTextNormalizer.normalizeForParsing(message.getString());
        WarDetector.WarResult warResult = detector.tryDetectOutcome(cleaned);
        if (warResult != null) {
            sendWarEvent(warResult);
        }
    }

    private void sendWarEvent(WarDetector.WarResult warResult) {
        JsonObject payload = new JsonObject();
        payload.addProperty("territory", warResult.territory());
        payload.addProperty("outcome", warResult.outcome());

        WarDetector.TowerStats stats = warResult.tower();
        JsonObject tower = new JsonObject();
        tower.addProperty("hp", stats.health());
        tower.addProperty("defense", stats.defense());
        tower.addProperty("damage_low", stats.damageLow());
        tower.addProperty("damage_high", stats.damageHigh());
        tower.addProperty("attack_speed", stats.attackSpeed());
        payload.add("tower", tower);

        JsonArray warrers = new JsonArray();
        for (String warrer : warResult.warrers()) {
            warrers.add(warrer);
        }
        payload.add("warrers", warrers);
        payload.addProperty("duration_seconds", warResult.durationSeconds());
        payload.addProperty("dps", warResult.dps());
        AvoWebSocketManager.getInstance().sendEvent(EVT_GUILD_WAR, payload);
    }

    @Override
    public void onServerScopeChanged(Scope newScope) {
        if (newScope != Scope.MAIN) {
            detector.reset();
        }
    }
}
