package info.avicia.partyfinder;

import info.avicia.partyfinder.api.PartyFinderClient;
import info.avicia.partyfinder.command.PartyCommand;
import info.avicia.partyfinder.config.ModConfig;
import info.avicia.partyfinder.handler.ChatPartyDetector;
import info.avicia.partyfinder.handler.InviteHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PartyFinderMod implements ClientModInitializer {
    public static final String MOD_ID = "avicia-pfinder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static PartyFinderMod instance;

    private ModConfig config;
    private PartyFinderClient apiClient;
    private ChatPartyDetector chatDetector;
    private InviteHandler inviteHandler;

    @Override
    public void onInitializeClient() {
        instance = this;

        // Load configs
        config = ModConfig.load();

        // Initialize API client
        apiClient = new PartyFinderClient(config);

        // Initialize handlers
        chatDetector = new ChatPartyDetector(apiClient);
        inviteHandler = new InviteHandler(chatDetector);

        // Client tick events
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            inviteHandler.tick(client);
        });

        // Register /apf client command
        PartyCommand.register(apiClient, chatDetector, inviteHandler);

        LOGGER.info("Avicia Party Finder mod initialized.");
    }

    public static PartyFinderMod getInstance() {
        return instance;
    }

    public ModConfig getConfig() {
        return config;
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
