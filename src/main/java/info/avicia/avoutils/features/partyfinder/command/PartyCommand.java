package info.avicia.avoutils.features.partyfinder.command;

import com.mojang.brigadier.Command;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.command.AvoCommands;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.util.WynnPillUtil;
import info.avicia.avoutils.features.partyfinder.PartyFinderFeature;
import info.avicia.avoutils.features.partyfinder.gui.PartyListScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

/**
 * Registers the {@code /apf} client-side command and owns the partyfinder command logic.
 */
public class PartyCommand {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            Command<FabricClientCommandSource> openScreenCommand = context -> {
                if (!AvoCommands.ensureNetworkingAllowed(context.getSource())) return 1;
                MinecraftClient.getInstance().execute(() -> openPartyFinderScreen(null));
                return 1;
            };

            Command<FabricClientCommandSource> joinPartyCommand = context -> {
                if (!AvoCommands.ensureNetworkingAllowed(context.getSource())) return 1;
                String leaderName = getString(context, "leaderName");
                MinecraftClient.getInstance().execute(() -> openPartyFinderScreen(leaderName));
                return 1;
            };

            // /apf
            dispatcher.register(
                    literal("apf")
                            .executes(openScreenCommand)
                            .then(literal("togglenotifs")
                                    .executes(toggleNotifsCommand()))
                            .then(literal("togglesounds")
                                    .executes(toggleSoundsCommand()))
                            .then(literal("join")
                                    .then(argument("leaderName", word())
                                            .executes(joinPartyCommand)))
            );
        });
    }

    public static void openPartyFinderScreen(String joinTargetLeaderName) {
        AvoUtilsMod mod = AvoUtilsMod.getInstance();
        PartyFinderFeature pfFeature = mod != null ? mod.getFeature(PartyFinderFeature.class) : null;
        if (pfFeature == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(new PartyListScreen(
                    pfFeature.getApiClient(),
                    pfFeature.getPartySyncer(),
                    pfFeature.getInviteHandler(),
                    joinTargetLeaderName
            ));
        }
    }

    public static Command<FabricClientCommandSource> toggleNotifsCommand() {
        return context -> {
            AvoUtilsMod mod = AvoUtilsMod.getInstance();
            if (mod == null) return 1;
            ModConfig config = mod.getConfig();
            config.newPartyNotifsEnabled = !config.newPartyNotifsEnabled;
            config.save();
            WynnPillUtil.sendToggleFeedback("AvoUtils", "New party notifications are now ", config.newPartyNotifsEnabled);
            return 1;
        };
    }

    public static Command<FabricClientCommandSource> toggleSoundsCommand() {
        return context -> {
            AvoUtilsMod mod = AvoUtilsMod.getInstance();
            if (mod == null) return 1;
            ModConfig config = mod.getConfig();
            config.notificationSoundsEnabled = !config.notificationSoundsEnabled;
            config.save();
            WynnPillUtil.sendToggleFeedback("AvoUtils", "Notification sounds are now ", config.notificationSoundsEnabled);
            return 1;
        };
    }
}
