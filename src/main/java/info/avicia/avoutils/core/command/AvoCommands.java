package info.avicia.avoutils.core.command;

import com.mojang.brigadier.Command;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.gui.config.ConfigScreen;
import info.avicia.avoutils.core.util.WynnPillUtil;
import info.avicia.avoutils.features.chatbridge.ChatBridgeFeature;
import info.avicia.avoutils.features.emojis.EmojiFeature;
import info.avicia.avoutils.features.guildstorage.GuildStorageNotifier;
import info.avicia.avoutils.features.anniparty.AnniPartyFeature;
import info.avicia.avoutils.features.anniparty.AnniPartyScreen;
import info.avicia.avoutils.features.partyfinder.PartyFinderFeature;
import info.avicia.avoutils.features.partyfinder.command.PartyCommand;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> client.setScreen(new ConfigScreen()));
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

            // /avo emojis reload / update → refresh and re-download emojis
            Command<FabricClientCommandSource> reloadEmojisCommand = context -> {
                EmojiFeature feature = AvoUtilsMod.getInstance().getFeature(EmojiFeature.class);
                if (feature != null) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null) {
                        client.player.sendMessage(
                                WynnPillUtil.createPrefixedPill("AvoUtils", false)
                                        .append(Text.literal("Checking and updating emojis...").formatted(Formatting.GRAY)),
                                false
                        );
                    }
                    feature.reloadEmojis().thenRun(() -> {
                        MinecraftClient c = MinecraftClient.getInstance();
                        if (c != null) {
                            c.execute(() -> {
                                if (c.player != null) {
                                    c.player.sendMessage(
                                            WynnPillUtil.createPrefixedPill("AvoUtils", false)
                                                    .append(Text.literal("Emojis updated successfully!").formatted(Formatting.GREEN)),
                                            false
                                    );
                                }
                            });
                        }
                    }).exceptionally(ex -> {
                        MinecraftClient c = MinecraftClient.getInstance();
                        if (c != null) {
                            c.execute(() -> {
                                if (c.player != null) {
                                    String err = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                                    c.player.sendMessage(
                                            WynnPillUtil.createPrefixedPill("AvoUtils", true)
                                                    .append(Text.literal("Failed to update emojis: " + err).formatted(Formatting.RED)),
                                            false
                                    );
                                }
                            });
                        }
                        return null;
                    });
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
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> {
                    AnniPartyFeature anniFeature = AvoUtilsMod.getInstance().getFeature(AnniPartyFeature.class);
                    PartyFinderFeature pfFeature = AvoUtilsMod.getInstance().getFeature(PartyFinderFeature.class);
                    if (anniFeature != null && pfFeature != null) {
                        client.setScreen(new AnniPartyScreen(anniFeature, pfFeature.getInviteHandler()));
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
                                    .executes(toggleEmojisCommand)
                                    .then(literal("reload")
                                            .executes(reloadEmojisCommand))
                                    .then(literal("update")
                                            .executes(reloadEmojisCommand)))
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
