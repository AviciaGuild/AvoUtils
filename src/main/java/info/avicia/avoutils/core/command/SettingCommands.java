package info.avicia.avoutils.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.config.Settings;
import info.avicia.avoutils.core.config.ToggleSetting;
import info.avicia.avoutils.core.util.WynnPillUtil;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Commands that flip toggle settings, such as /avo bridge, built from the declarations in
 * {@link Settings}. Brigadier merges them into nodes that already exist, such as /avo pf.
 */
public final class SettingCommands {

    private SettingCommands() {
    }

    /** Registers each toggle's command under {@code root}, e.g. /avo pf togglesounds. */
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, String root) {
        for (ToggleSetting toggle : Settings.toggles()) {
            if (toggle.chatCommand() != null) {
                register(dispatcher, root, toggle.chatCommand().path(), toggle);
            }
        }
    }

    /**
     * Registers the toggle commands that start with {@code prefix} under {@code alias} instead, so
     * /apf togglesounds works like /avo pf togglesounds.
     */
    public static void registerAlias(CommandDispatcher<FabricClientCommandSource> dispatcher, String alias, String prefix) {
        for (ToggleSetting toggle : Settings.toggles()) {
            List<String> path = toggle.chatCommand() != null ? toggle.chatCommand().path() : List.of();
            if (path.size() > 1 && path.getFirst().equals(prefix)) {
                register(dispatcher, alias, path.subList(1, path.size()), toggle);
            }
        }
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, String root,
                                 List<String> path, ToggleSetting toggle) {
        LiteralArgumentBuilder<FabricClientCommandSource> node = literal(path.getLast())
                .executes(context -> toggle(toggle));
        for (int i = path.size() - 2; i >= 0; i--) {
            node = literal(path.get(i)).then(node);
        }
        dispatcher.register(literal(root).then(node));
    }

    /** Flips the setting and reports the result in chat. */
    static int toggle(ToggleSetting toggle) {
        AvoUtilsMod mod = AvoUtilsMod.getInstance();
        if (mod == null) {
            return 1;
        }
        ModConfig config = mod.getConfig();
        ToggleSetting.ChatCommand command = toggle.chatCommand();
        toggle.change(config, !toggle.get(config),
                () -> WynnPillUtil.sendToggleFeedback(command.pill(), command.feedbackPrefix(), toggle.get(config)),
                () -> WynnPillUtil.sendNotMemberFeedback(command.pill(), toggle.notMemberMessage()));
        return 1;
    }
}

