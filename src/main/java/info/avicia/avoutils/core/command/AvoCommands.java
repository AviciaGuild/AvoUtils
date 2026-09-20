package info.avicia.avoutils.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.gui.config.ConfigScreen;
import info.avicia.avoutils.features.anniparty.AnniPartyFeature;
import info.avicia.avoutils.features.anniparty.AnniPartyScreen;
import info.avicia.avoutils.features.chatbridge.ChatBridgeFeature;
import info.avicia.avoutils.features.emojis.EmojiFeature;
import info.avicia.avoutils.features.guildstorage.GuildStorageNotifier;
import info.avicia.avoutils.features.partyfinder.PartyFinderFeature;
import info.avicia.avoutils.features.partyfinder.command.PartyCommand;
import info.avicia.avoutils.features.updater.UpdateFeature;
import info.avicia.avoutils.core.util.WynnPillUtil;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.function.Consumer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

/**
 * Registration for all /avo and /avoutils subcommands.
 */
public class AvoCommands {
    public static final List<String> COMMAND_ROOTS = List.of("avo", "avoutils");

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> register(dispatcher));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // /avo and /avo config → opens config screen
        Command<FabricClientCommandSource> openConfigCommand = context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> client.setScreen(new ConfigScreen()));
            return 1;
        };

        // /avo bridge → toggle chat bridge
        Command<FabricClientCommandSource> toggleBridgeCommand = context ->
                withFeature(ChatBridgeFeature.class, ChatBridgeFeature::toggleBridge);

        // /avo storage → toggle guild storage tracking
        Command<FabricClientCommandSource> toggleStorageCommand = context ->
                withFeature(GuildStorageNotifier.class, GuildStorageNotifier::toggleStorage);

        // /avo emojis → toggle emoji feature
        Command<FabricClientCommandSource> toggleEmojisCommand = context ->
                withFeature(EmojiFeature.class, EmojiFeature::toggleEmojis);

        // /avo emojis reload / update → refresh and re-download emojis
        Command<FabricClientCommandSource> reloadEmojisCommand = context -> {
            if (!ensureOnWynncraft(context.getSource())) return 1;
            return withFeature(EmojiFeature.class, EmojiFeature::handleReloadCommand);
        };

        // /avo pf → open party finder screen
        Command<FabricClientCommandSource> openPfCommand = context -> {
            if (!ensureNetworkingAllowed(context.getSource())) return 1;
            MinecraftClient.getInstance().execute(() -> PartyCommand.openPartyFinderScreen(null));
            return 1;
        };

        // /avo anni → open anni party screen
        Command<FabricClientCommandSource> openAnniCommand = context -> {
            if (!ensureNetworkingAllowed(context.getSource())) return 1;
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
            if (!ensureNetworkingAllowed(context.getSource())) return 1;
            String leaderName = getString(context, "leaderName");
            MinecraftClient.getInstance().execute(() -> PartyCommand.openPartyFinderScreen(leaderName));
            return 1;
        };

        // /avo update → check for update and show status
        Command<FabricClientCommandSource> updateCommand = context -> {
            if (!ensureNetworkingAllowed(context.getSource())) return 1;
            return withFeature(UpdateFeature.class, f -> f.handleCheckCommand(false));
        };

        // /avo update check → force re-check
        Command<FabricClientCommandSource> updateCheckCommand = context -> {
            if (!ensureNetworkingAllowed(context.getSource())) return 1;
            return withFeature(UpdateFeature.class, f -> f.handleCheckCommand(true));
        };

        // /avo update download → download and stage update
        Command<FabricClientCommandSource> updateDownloadCommand = context -> {
            if (!ensureNetworkingAllowed(context.getSource())) return 1;
            return withFeature(UpdateFeature.class, UpdateFeature::handleDownloadCommand);
        };

        // /avo update restart → restart Minecraft to apply staged update
        Command<FabricClientCommandSource> updateRestartCommand = context -> {
            if (!ensureNetworkingAllowed(context.getSource())) return 1;
            return withFeature(UpdateFeature.class, UpdateFeature::handleRestartCommand);
        };

        // Build /avo and /avoutils command trees
        for (String root : COMMAND_ROOTS) {
            dispatcher.register(
                    literal(root)
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
                            .then(literal("update")
                                    .executes(updateCommand)
                                    .then(literal("check")
                                            .executes(updateCheckCommand))
                                    .then(literal("download")
                                            .executes(updateDownloadCommand))
                                    .then(literal("restart")
                                            .executes(updateRestartCommand)))
            );
        }
    }

    public static boolean ensureNetworkingAllowed(FabricClientCommandSource source) {
        if (!WynncraftServerPolicy.isOnWynncraft()) {
            sendDisabledFeedback(source, WynncraftServerPolicy.NOT_ON_WYNCRAFT_MESSAGE);
            return false;
        }
        if (!WynncraftServerPolicy.isNetworkingAllowed()) {
            sendDisabledFeedback(source, WynncraftServerPolicy.BETA_NETWORKING_BLOCKED_MESSAGE);
            return false;
        }
        return true;
    }

    private static boolean ensureOnWynncraft(FabricClientCommandSource source) {
        if (!WynncraftServerPolicy.isOnWynncraft()) {
            sendDisabledFeedback(source, WynncraftServerPolicy.NOT_ON_WYNCRAFT_MESSAGE);
            return false;
        }
        return true;
    }

    public static void sendDisabledFeedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(WynnPillUtil.createPrefixedPill("AvoUtils", true)
                .append(Text.literal(message).formatted(Formatting.RED)));
    }

    private static <T extends AvoFeature> int withFeature(Class<T> cls, Consumer<T> action) {
        AvoUtilsMod mod = AvoUtilsMod.getInstance();
        if (mod == null) return 1;
        T feature = mod.getFeature(cls);
        if (feature != null) {
            action.accept(feature);
        }
        return 1;
    }
}
