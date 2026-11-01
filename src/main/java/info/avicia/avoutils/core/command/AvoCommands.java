package info.avicia.avoutils.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import info.avicia.avoutils.core.AvoFeature;
import info.avicia.avoutils.core.gui.config.ConfigScreen;
import info.avicia.avoutils.core.util.WynnPillUtil;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Registration for the /avo and /avoutils roots. Features add their own subcommands through
 * {@link AvoFeature#registerCommands}.
 */
public class AvoCommands {
    public static final List<String> COMMAND_ROOTS = List.of("avo", "avoutils");

    public static void register(List<? extends AvoFeature> features) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> register(dispatcher, features));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                List<? extends AvoFeature> features) {
        // /avo and /avo config → opens config screen
        Command<FabricClientCommandSource> openConfigCommand = context -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> client.setScreen(new ConfigScreen()));
            return 1;
        };

        // Build /avo and /avoutils command trees
        for (String root : COMMAND_ROOTS) {
            LiteralArgumentBuilder<FabricClientCommandSource> tree = literal(root)
                    .executes(openConfigCommand)
                    .then(literal("config")
                            .executes(openConfigCommand));
            for (AvoFeature feature : features) {
                feature.registerCommands(tree);
            }
            dispatcher.register(tree);
            // Setting toggles
            SettingCommands.register(dispatcher, root);
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

    public static boolean ensureOnWynncraft(FabricClientCommandSource source) {
        if (!WynncraftServerPolicy.isOnWynncraft()) {
            sendDisabledFeedback(source, WynncraftServerPolicy.NOT_ON_WYNCRAFT_MESSAGE);
            return false;
        }
        return true;
    }

    public static void sendDisabledFeedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(WynnPillUtil.createPrefixedPill("AvoUtils", true)
                .append(Component.literal(message).withStyle(ChatFormatting.RED)));
    }
}
