package info.avicia.avoutils.features.partyfinder;
 
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import info.avicia.avoutils.features.partyfinder.command.PartyCommand;
import info.avicia.avoutils.features.partyfinder.handler.ChatPartyDetector;
import info.avicia.avoutils.features.partyfinder.handler.InviteHandler;
import info.avicia.avoutils.features.partyfinder.handler.PartyFinderNotificationHandler;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Wrapper for all pfinder systems
 */
public class PartyFinderFeature implements AvoFeature {
    private ModConfig config;
    private PartyFinderClient apiClient;
    private ChatPartyDetector chatDetector;
    private InviteHandler inviteHandler;
    private PartyFinderNotificationHandler notificationHandler;
 
    @Override
    public void initialize(ModConfig config) {
        this.config = config;

        // Initialize API client
        apiClient = new PartyFinderClient(config);
 
        // Initialize handlers
        chatDetector = new ChatPartyDetector(apiClient);
        inviteHandler = new InviteHandler(chatDetector);
        notificationHandler = new PartyFinderNotificationHandler(config);
        notificationHandler.register();
 
        // Client tick events
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            inviteHandler.tick(client);
        });
 
        // Register connection demand for the WebSocket
        AvoWebSocketManager.getInstance().registerConnectionDemand("partyfinder", () -> true);

        // Register client commands
        PartyCommand.register(apiClient, chatDetector, inviteHandler, config);
    }
 
    public ChatPartyDetector getChatDetector() {
        return chatDetector;
    }
}
