package info.avicia.avoutils.core;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import info.avicia.avoutils.core.config.ModConfig;
import info.avicia.avoutils.core.util.WynncraftServerPolicy.Scope;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Base interface for all modular features in AvoUtils
 */
public interface AvoFeature {
    void initialize(ModConfig config);

    default void onServerScopeChanged(Scope newScope) {}

    /** Adds this feature's subcommands to an {@code /avo} or {@code /avoutils} root. */
    default void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {}
}
