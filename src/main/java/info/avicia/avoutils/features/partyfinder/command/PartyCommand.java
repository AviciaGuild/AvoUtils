package info.avicia.avoutils.features.partyfinder.command;

import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import info.avicia.avoutils.features.partyfinder.gui.PartyListScreen;
import info.avicia.avoutils.features.partyfinder.handler.ChatPartyDetector;
import info.avicia.avoutils.features.partyfinder.handler.InviteHandler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;

/**
 * Registers the {@code /apf} and {@code /avo pf} client-side commands
 */
public class PartyCommand {
    public static void register(PartyFinderClient apiClient, ChatPartyDetector chatDetector, InviteHandler inviteHandler) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            com.mojang.brigadier.Command<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> openScreenCommand = context -> {
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.setScreen(new PartyListScreen(apiClient, chatDetector, inviteHandler));
                });
                return 1;
            };

            // Register /apf
            dispatcher.register(
                    ClientCommandManager.literal("apf")
                            .executes(openScreenCommand)
            );

            // Register /avo pf
            dispatcher.register(
                    ClientCommandManager.literal("avo")
                            .then(ClientCommandManager.literal("pf")
                                    .executes(openScreenCommand)
                            )
            );
        });
    }
}
