package info.avicia.avoutils.features.partyfinder.command;

import com.mojang.brigadier.Command;
import info.avicia.avoutils.core.command.AvoCommands;
import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import info.avicia.avoutils.features.partyfinder.gui.PartyListScreen;
import info.avicia.avoutils.features.partyfinder.handler.ChatPartyDetector;
import info.avicia.avoutils.features.partyfinder.handler.InviteHandler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

/**
 * Registers the {@code /apf} client-side command
 */
public class PartyCommand {
    public static void register(PartyFinderClient apiClient, ChatPartyDetector chatDetector,
                                 InviteHandler inviteHandler) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            Command<FabricClientCommandSource> openScreenCommand = context -> {
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.setScreen(new PartyListScreen(apiClient, chatDetector, inviteHandler));
                });
                return 1;
            };

            Command<FabricClientCommandSource> joinPartyCommand = context -> {
                String leaderName = getString(context, "leaderName");
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.setScreen(new PartyListScreen(apiClient, chatDetector, inviteHandler, leaderName));
                });
                return 1;
            };

            // /apf
            dispatcher.register(
                    literal("apf")
                            .executes(openScreenCommand)
                            .then(literal("togglenotifs")
                                    .executes(AvoCommands.toggleNotifsCommand()))
                            .then(literal("togglesounds")
                                    .executes(AvoCommands.toggleSoundsCommand()))
                            .then(literal("join")
                                    .then(argument("leaderName", word())
                                            .executes(joinPartyCommand)))
            );
        });
    }
}
