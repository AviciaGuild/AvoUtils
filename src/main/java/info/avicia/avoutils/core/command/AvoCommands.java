package info.avicia.avoutils.core.command;

import com.mojang.brigadier.Command;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.gui.config.ConfigScreen;
import info.avicia.avoutils.features.chatbridge.ChatBridgeFeature;
import info.avicia.avoutils.features.emojis.EmojiFeature;
import info.avicia.avoutils.features.partyfinder.PartyFinderFeature;
import info.avicia.avoutils.features.partyfinder.gui.PartyListScreen;
import info.avicia.avoutils.core.util.WynnPillUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

/**
 * Registration for all /avo subcommands
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
                MinecraftClient.getInstance().execute(() -> {
                    PartyFinderFeature pfFeature = AvoUtilsMod.getInstance().getFeature(PartyFinderFeature.class);
                    if (pfFeature != null) {
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.setScreen(new PartyListScreen(
                                pfFeature.getApiClient(),
                                pfFeature.getChatDetector(),
                                pfFeature.getInviteHandler()
                        ));
                    }
                });
                return 1;
            };

            // /avo pf togglenotifs
            Command<FabricClientCommandSource> toggleNotifsCommand = toggleNotifsCommand();

            // /avo pf togglesounds
            Command<FabricClientCommandSource> toggleSoundsCommand = toggleSoundsCommand();

            // /avo pf join <leaderName>
            Command<FabricClientCommandSource> joinPfCommand = context -> {
                String leaderName = getString(context, "leaderName");
                MinecraftClient.getInstance().execute(() -> {
                    PartyFinderFeature pfFeature = AvoUtilsMod.getInstance().getFeature(PartyFinderFeature.class);
                    if (pfFeature != null) {
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.setScreen(new PartyListScreen(
                                pfFeature.getApiClient(),
                                pfFeature.getChatDetector(),
                                pfFeature.getInviteHandler(),
                                leaderName
                        ));
                    }
                });
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
                            .then(literal("emojis")
                                    .executes(toggleEmojisCommand))
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

    public static Command<FabricClientCommandSource> toggleNotifsCommand() {
        return context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            ModConfig config = AvoUtilsMod.getInstance().getConfig();
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
    }

    public static Command<FabricClientCommandSource> toggleSoundsCommand() {
        return context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            ModConfig config = AvoUtilsMod.getInstance().getConfig();
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
    }
}
