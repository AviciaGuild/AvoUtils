package info.avicia.avoutils.features.partyfinder.command;

import com.mojang.brigadier.Command;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.util.WynnPillUtil;
import info.avicia.avoutils.features.partyfinder.PartyFinderFeature;
import info.avicia.avoutils.features.partyfinder.gui.PartyListScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
                MinecraftClient.getInstance().execute(() -> openPartyFinderScreen(null));
                return 1;
            };

            Command<FabricClientCommandSource> joinPartyCommand = context -> {
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
        PartyFinderFeature pfFeature = AvoUtilsMod.getInstance().getFeature(PartyFinderFeature.class);
        if (pfFeature == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new PartyListScreen(
                pfFeature.getApiClient(),
                pfFeature.getPartySyncer(),
                pfFeature.getInviteHandler(),
                joinTargetLeaderName
        ));
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
