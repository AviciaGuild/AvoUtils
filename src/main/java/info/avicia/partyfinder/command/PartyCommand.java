package info.avicia.partyfinder.command;

import info.avicia.partyfinder.api.PartyFinderClient;
import info.avicia.partyfinder.gui.PartyListScreen;
import info.avicia.partyfinder.handler.ChatPartyDetector;
import info.avicia.partyfinder.handler.InviteHandler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;

/**
 * Registers the {@code /apf} client-side command
 */
public class PartyCommand {
    public static void register(PartyFinderClient apiClient, ChatPartyDetector chatDetector, InviteHandler inviteHandler) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("apf")
                            .executes(context -> {
                                MinecraftClient.getInstance().execute(() -> {
                                    MinecraftClient client = MinecraftClient.getInstance();
                                    client.setScreen(new PartyListScreen(apiClient, chatDetector, inviteHandler));
                                });
                                return 1;
                            })
            );
        });
    }
}
