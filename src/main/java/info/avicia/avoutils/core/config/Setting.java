package info.avicia.avoutils.core.config;

/**
 * A user-facing setting, declared once in {@link Settings}: which {@link ModConfig} field holds it,
 * and how it appears on the config screen.
 */
public sealed interface Setting permits ToggleSetting, SliderSetting {

    /** The config screen card it's shown in. */
    ConfigSection section();

    /** Its row label on the config screen. */
    String label();

    /** Toggle that must be on for this setting to be editable, or null. */
    ToggleSetting parent();

    /** Puts an invalid stored value back to its default. Runs when the config file is loaded. */
    default void validate(ModConfig config) {
    }

    /**
     * Whether the player can change this setting right now. Its parent toggle must be on, and usable
     * if it's guild-only. A guild-only toggle that's off can only be turned on with guild access.
     *
     * @param guildAccess whether the player is a guild member, or membership isn't known yet
     */
    default boolean isEditable(ModConfig config, boolean guildAccess) {
        if (this instanceof ToggleSetting toggle && toggle.isGuildOnly() && !toggle.get(config) && !guildAccess) {
            return false;
        }
        ToggleSetting parent = parent();
        return parent == null || (parent.get(config) && (!parent.isGuildOnly() || guildAccess));
    }
}

