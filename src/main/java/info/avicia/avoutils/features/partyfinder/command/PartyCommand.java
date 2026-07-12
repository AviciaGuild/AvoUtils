package info.avicia.avoutils.features.partyfinder.command;

import com.mojang.brigadier.Command;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.features.partyfinder.api.PartyFinderClient;
import info.avicia.avoutils.features.partyfinder.gui.PartyListScreen;
import info.avicia.avoutils.features.partyfinder.handler.ChatPartyDetector;
import info.avicia.avoutils.features.partyfinder.handler.InviteHandler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import info.avicia.avoutils.core.util.WynnPillUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

/**
 * Registers the {@code /apf} and {@code /avo pf} client-side commands
 */
public class PartyCommand {
    public static void register(PartyFinderClient apiClient, ChatPartyDetector chatDetector,
                                 InviteHandler inviteHandler, ModConfig config) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            Command<FabricClientCommandSource> openScreenCommand = context -> {
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.setScreen(new PartyListScreen(apiClient, chatDetector, inviteHandler));
                });
                return 1;
            };

            Command<FabricClientCommandSource> toggleNotifsCommand = context -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                config.newPartyNotifsEnabled = !config.newPartyNotifsEnabled;
                config.save();
                if (mc.player != null) {
                    String status = config.newPartyNotifsEnabled ? "enabled" : "disabled";
                    Formatting color = config.newPartyNotifsEnabled ? Formatting.GREEN : Formatting.RED;
                    MutableText msg = WynnPillUtil.createPrefixedPill("AvoUtils", false)
                            .append(Text.literal("New party notifications are now ").formatted(Formatting.GRAY))
                            .append(Text.literal(status).formatted(color))
                            .append(Text.literal(".").formatted(Formatting.GRAY));
                    mc.player.sendMessage(msg, false);
                }
                return 1;
            };

            Command<FabricClientCommandSource> toggleSoundsCommand = context -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                config.notificationSoundsEnabled = !config.notificationSoundsEnabled;
                config.save();
                if (mc.player != null) {
                    String status = config.notificationSoundsEnabled ? "enabled" : "disabled";
                    Formatting color = config.notificationSoundsEnabled ? Formatting.GREEN : Formatting.RED;
                    MutableText msg = WynnPillUtil.createPrefixedPill("AvoUtils", false)
                            .append(Text.literal("Notification sounds are now ").formatted(Formatting.GRAY))
                            .append(Text.literal(status).formatted(color))
                            .append(Text.literal(".").formatted(Formatting.GRAY));
                    mc.player.sendMessage(msg, false);
                }
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
                                    .executes(toggleNotifsCommand))
                            .then(literal("togglesounds")
                                    .executes(toggleSoundsCommand))
                            .then(literal("join")
                                    .then(argument("leaderName", word())
                                            .executes(joinPartyCommand)))
            );

            // /avo pf
            dispatcher.register(
                    literal("avo")
                            .then(literal("pf")
                                    .executes(openScreenCommand)
                                    .then(literal("togglenotifs")
                                            .executes(toggleNotifsCommand))
                                    .then(literal("togglesounds")
                                            .executes(toggleSoundsCommand))
                                    .then(literal("join")
                                            .then(argument("leaderName", word())
                                                    .executes(joinPartyCommand))))
            );
        });
    }
}
