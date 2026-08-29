package info.avicia.avoutils.features.partyfinder;
 
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import info.avicia.avoutils.features.partyfinder.command.PartyCommand;
import info.avicia.avoutils.features.partyfinder.handler.PartyFinderPartySyncer;
import info.avicia.avoutils.core.party.InviteHandler;
import info.avicia.avoutils.features.partyfinder.handler.PartyFinderNotificationHandler;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Wrapper for all pfinder systems
 */
public class PartyFinderFeature implements AvoFeature {
    private ModConfig config;
    private PartyFinderClient apiClient;
    private PartyFinderPartySyncer partySyncer;
    private InviteHandler inviteHandler;
    private PartyFinderNotificationHandler notificationHandler;
 
    @Override
    public void initialize(ModConfig config) {
        this.config = config;

        // Initialize API client
        apiClient = new PartyFinderClient(config);
 
        // Initialize handlers
        partySyncer = new PartyFinderPartySyncer(apiClient);
        inviteHandler = new InviteHandler();
        notificationHandler = new PartyFinderNotificationHandler(config);
        notificationHandler.register();
 
        // Client tick events
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            inviteHandler.tick(client);
        });
 
        // Register connection demand for the WebSocket
        AvoWebSocketManager.getInstance().registerConnectionDemand("partyfinder", () -> true);

        // Register client commands
        PartyCommand.register();
    }
 
    public PartyFinderPartySyncer getPartySyncer() {
        return partySyncer;
    }

    public PartyFinderClient getApiClient() {
        return apiClient;
    }

    public InviteHandler getInviteHandler() {
        return inviteHandler;
    }
}
