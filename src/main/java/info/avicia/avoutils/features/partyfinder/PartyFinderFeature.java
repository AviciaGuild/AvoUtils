package info.avicia.avoutils.features.partyfinder;
 
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import info.avicia.avoutils.features.partyfinder.command.PartyCommand;
import info.avicia.avoutils.features.partyfinder.handler.ChatPartyDetector;
import info.avicia.avoutils.features.partyfinder.handler.InviteHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Wrapper for all pfinder systems
 */
public class PartyFinderFeature implements AvoFeature {
    private PartyFinderClient apiClient;
    private ChatPartyDetector chatDetector;
    private InviteHandler inviteHandler;
 
    @Override
    public void initialize(ModConfig config) {
        // Initialize API client
        apiClient = new PartyFinderClient(config);
 
        // Initialize handlers
        chatDetector = new ChatPartyDetector(apiClient);
        inviteHandler = new InviteHandler(chatDetector);
 
        // Client tick events
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            inviteHandler.tick(client);
        });
 
        // Register client commands
        PartyCommand.register(apiClient, chatDetector, inviteHandler);
    }
 
    public PartyFinderClient getApiClient() {
        return apiClient;
    }
 
    public ChatPartyDetector getChatDetector() {
        return chatDetector;
    }
 
    public InviteHandler getInviteHandler() {
        return inviteHandler;
    }
}
