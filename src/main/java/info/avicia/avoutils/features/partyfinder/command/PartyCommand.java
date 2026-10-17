package info.avicia.avoutils.features.partyfinder.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.command.AvoCommands;
import info.avicia.avoutils.core.command.SettingCommands;
import info.avicia.avoutils.features.partyfinder.PartyFinderFeature;
import info.avicia.avoutils.features.partyfinder.gui.PartyListScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

/**
 * Registers the {@code /apf} client-side command and owns the partyfinder command logic.
 */
public class PartyCommand {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> register(dispatcher));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        Command<FabricClientCommandSource> openScreenCommand = context -> {
            if (!AvoCommands.ensureNetworkingAllowed(context.getSource())) return 1;
            Minecraft.getInstance().execute(() -> openPartyFinderScreen(null));
            return 1;
        };

        Command<FabricClientCommandSource> joinPartyCommand = context -> {
            if (!AvoCommands.ensureNetworkingAllowed(context.getSource())) return 1;
            String leaderName = getString(context, "leaderName");
            Minecraft.getInstance().execute(() -> openPartyFinderScreen(leaderName));
            return 1;
        };

        // /apf
        dispatcher.register(
                literal("apf")
                        .executes(openScreenCommand)
                        .then(literal("join")
                                .then(argument("leaderName", word())
                                        .executes(joinPartyCommand)))
        );
        // /apf togglenotifs and togglesounds, the same setting toggles as /avo pf
        SettingCommands.registerAlias(dispatcher, "apf", "pf");
    }

    public static void openPartyFinderScreen(String joinTargetLeaderName) {
        AvoUtilsMod mod = AvoUtilsMod.getInstance();
        PartyFinderFeature pfFeature = mod != null ? mod.getFeature(PartyFinderFeature.class) : null;
        if (pfFeature == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.setScreen(new PartyListScreen(
                    pfFeature.getApiClient(),
                    pfFeature.getPartySyncer(),
                    pfFeature.getInviteHandler(),
                    joinTargetLeaderName
            ));
        }
    }
}
