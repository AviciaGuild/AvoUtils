package info.avicia.avoutils.features.partyfinder;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.command.AvoCommands;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import info.avicia.avoutils.features.partyfinder.command.PartyCommand;
import info.avicia.avoutils.features.partyfinder.handler.PartyFinderPartySyncer;
import info.avicia.avoutils.core.party.InviteHandler;
import info.avicia.avoutils.features.partyfinder.handler.PartyFinderNotificationHandler;
import info.avicia.avoutils.core.util.WynncraftServerPolicy.Scope;
import info.avicia.avoutils.core.websocket.AvoWebSocketManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

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
        ClientTickEvents.END_CLIENT_TICK.register(client -> inviteHandler.tick(client));

        // Register connection demand for the WebSocket
        AvoWebSocketManager.getInstance().registerConnectionDemand("partyfinder", () -> true);

        // Register client commands
        PartyCommand.register();
    }

    @Override
    public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        // /avo pf → open party finder screen; /avo pf join <leaderName>
        root.then(literal("pf")
                .executes(context -> openPartyFinder(context.getSource(), null))
                .then(literal("join")
                        .then(argument("leaderName", word())
                                .executes(context -> openPartyFinder(context.getSource(),
                                        getString(context, "leaderName"))))));
    }

    private static int openPartyFinder(FabricClientCommandSource source, String leaderName) {
        if (AvoCommands.ensureNetworkingAllowed(source)) {
            Minecraft.getInstance().execute(() -> PartyCommand.openPartyFinderScreen(leaderName));
        }
        return 1;
    }

    @Override
    public void onServerScopeChanged(Scope newScope) {
        if (newScope != Scope.MAIN && partySyncer != null) {
            partySyncer.clearTracking();
        }
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
