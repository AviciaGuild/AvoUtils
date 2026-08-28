package info.avicia.avoutils.core.command;

import com.mojang.brigadier.Command;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.gui.config.ConfigScreen;
import info.avicia.avoutils.features.chatbridge.ChatBridgeFeature;
import info.avicia.avoutils.features.emojis.EmojiFeature;
import info.avicia.avoutils.features.guildstorage.GuildStorageNotifier;
import info.avicia.avoutils.features.anniparty.AnniPartyFeature;
import info.avicia.avoutils.features.anniparty.AnniPartyScreen;
import info.avicia.avoutils.features.partyfinder.command.PartyCommand;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

/**
 * Registration for all /avo subcommands.
 */
public class AvoCommands {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // /avo and /avo config → opens config screen
            Command<FabricClientCommandSource> openConfigCommand = context -> {
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.setScreen(new ConfigScreen());
                });
                return 1;
            };

            // /avo bridge → toggle chat bridge
            Command<FabricClientCommandSource> toggleBridgeCommand = context -> {
                ChatBridgeFeature feature = AvoUtilsMod.getInstance().getFeature(ChatBridgeFeature.class);
                if (feature != null) {
                    feature.toggleBridge();
                }
                return 1;
            };

            // /avo storage → toggle guild storage tracking
            Command<FabricClientCommandSource> toggleStorageCommand = context -> {
                GuildStorageNotifier feature = AvoUtilsMod.getInstance().getFeature(GuildStorageNotifier.class);
                if (feature != null) {
                    feature.toggleStorage();
                }
                return 1;
            };

            // /avo emojis → toggle emoji feature
            Command<FabricClientCommandSource> toggleEmojisCommand = context -> {
                EmojiFeature feature = AvoUtilsMod.getInstance().getFeature(EmojiFeature.class);
                if (feature != null) {
                    feature.toggleEmojis();
                }
                return 1;
            };

            // /avo pf → open party finder screen
            Command<FabricClientCommandSource> openPfCommand = context -> {
                MinecraftClient.getInstance().execute(() -> PartyCommand.openPartyFinderScreen(null));
                return 1;
            };

            // /avo anni → open anni party screen
            Command<FabricClientCommandSource> openAnniCommand = context -> {
                MinecraftClient.getInstance().execute(() -> {
                    AnniPartyFeature anniFeature = AvoUtilsMod.getInstance().getFeature(AnniPartyFeature.class);
                    if (anniFeature != null) {
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.setScreen(new AnniPartyScreen(anniFeature));
                    }
                });
                return 1;
            };

            // /avo pf togglenotifs
            Command<FabricClientCommandSource> toggleNotifsCommand = PartyCommand.toggleNotifsCommand();

            // /avo pf togglesounds
            Command<FabricClientCommandSource> toggleSoundsCommand = PartyCommand.toggleSoundsCommand();

            // /avo pf join <leaderName>
            Command<FabricClientCommandSource> joinPfCommand = context -> {
                String leaderName = getString(context, "leaderName");
                MinecraftClient.getInstance().execute(() -> PartyCommand.openPartyFinderScreen(leaderName));
                return 1;
            };

            // Build /avo command tree
            dispatcher.register(
                    literal("avo")
                            .executes(openConfigCommand)
                            .then(literal("config")
                                    .executes(openConfigCommand))
                            .then(literal("bridge")
                                    .executes(toggleBridgeCommand))
                            .then(literal("storage")
                                    .executes(toggleStorageCommand))
                            .then(literal("emojis")
                                    .executes(toggleEmojisCommand))
                            .then(literal("anni")
                                    .executes(openAnniCommand))
                            .then(literal("pf")
                                    .executes(openPfCommand)
                                    .then(literal("togglenotifs")
                                            .executes(toggleNotifsCommand))
                                    .then(literal("togglesounds")
                                            .executes(toggleSoundsCommand))
                                    .then(literal("join")
                                            .then(argument("leaderName", word())
                                                    .executes(joinPfCommand))))
            );
        });
    }
}
