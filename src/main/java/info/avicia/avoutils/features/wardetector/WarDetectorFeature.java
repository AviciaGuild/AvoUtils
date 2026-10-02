package info.avicia.avoutils.features.wardetector;

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
import net.minecraft.text.Text;

/**
 * Feature that detects guild war outcomes and participants, relaying results to the Avicia gateway.
 */
public class WarDetectorFeature implements AvoFeature {

    private static final String AVO_ICON_URL =
            "https://raw.githubusercontent.com/AviciaGuild/AvoUtils/refs/heads/main/src/main/resources/assets/avoutils/icon.png";
    private static final String EVT_GUILD_WAR = "guild_war_result";

    public boolean isGuildMember() {
        return AvoAuthService.getInstance().isGuildMember();
    }

    @Override
    public void initialize(ModConfig config) {
        // Continuous client tick for war tracking while on Wynncraft
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (isGuildMember() && WynncraftServerPolicy.isOnWynncraft()) {
                WarDetector.tick();
            }
        });

        // Reset war tracking state on disconnect
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WarDetector.reset());

        // Register connection demand lease for verified guild members
        AvoWebSocketManager.getInstance().registerConnectionDemand("wardetector", this::isGuildMember);

        AvoUtilsMod.LOGGER.info("[AvoUtils] [WarDetector] Initialized.");
    }

    /**
     * Called when a system chat packet is received.
     */
    public void onSystemChat(Text message) {
        if (message == null || !isGuildMember()) return;
        if (!AvoWebSocketManager.getInstance().isConnected()) return;

        String cleaned = PacketTextNormalizer.normalizeForParsing(message.getString());
        WarDetector.WarResult warResult = WarDetector.tryDetectOutcome(cleaned);
        if (warResult != null) {
            sendWarEvent(warResult);
        }
    }

    private void sendWarEvent(WarDetector.WarResult warResult) {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", "War Result");
        payload.addProperty("message", warResult.formattedMessage());
        payload.addProperty("avatar_url", AVO_ICON_URL);
        payload.addProperty("territory", warResult.territory());
        payload.addProperty("outcome", warResult.outcome());
        payload.addProperty("stats", warResult.stats());
        payload.addProperty("warrers", warResult.warrers());
        payload.addProperty("duration_seconds", warResult.durationSeconds());
        payload.addProperty("dps", warResult.dps());
        AvoWebSocketManager.getInstance().sendEvent(EVT_GUILD_WAR, payload);
    }

    @Override
    public void onServerScopeChanged(Scope newScope) {
        if (newScope != Scope.MAIN) {
            WarDetector.reset();
        }
    }
}
