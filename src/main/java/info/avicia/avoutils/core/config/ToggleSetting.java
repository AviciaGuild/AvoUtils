package info.avicia.avoutils.core.config;

import info.avicia.avoutils.core.auth.AvoAuthService;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An on/off setting. Declared with {@link #of} plus the chained methods below, e.g.
 * {@code ToggleSetting.of(...).requires(parent).command("pf togglesounds", ...)}.
 *
 * @param notMemberMessage shown to non-members who try to turn it on; null if anyone can
 * @param changeHook       runs with the new value after each change is saved; may be null
 * @param chatCommand      the command that flips it; null if it has none
 */
public record ToggleSetting(
        ConfigSection section,
        String label,
        Predicate<ModConfig> getter,
        BiConsumer<ModConfig, Boolean> setter,
        ToggleSetting parent,
        String notMemberMessage,
        Consumer<Boolean> changeHook,
        ChatCommand chatCommand) implements Setting {

    /**
     * A command under each /avo root that flips the toggle and reports the new state in chat.
     *
     * @param path           words after the root, e.g. {@code [pf, togglesounds]}
     * @param pill           label of the chat pill the feedback starts with
     * @param feedbackPrefix e.g. {@code "Emojis are now "}, followed by "enabled." or "disabled."
     */
    public record ChatCommand(List<String> path, String pill, String feedbackPrefix) {
    }

    public static ToggleSetting of(ConfigSection section, String label,
                                   Predicate<ModConfig> getter, BiConsumer<ModConfig, Boolean> setter) {
        return new ToggleSetting(section, label, getter, setter, null, null, null, null);
    }

    /** Only editable while {@code parent} is on. */
    public ToggleSetting requires(ToggleSetting parent) {
        return new ToggleSetting(section, label, getter, setter, parent, notMemberMessage, changeHook, chatCommand);
    }

    /** Only guild members can turn it on; anyone can turn it off. */
    public ToggleSetting guildOnly(String notMemberMessage) {
        return new ToggleSetting(section, label, getter, setter, parent, notMemberMessage, changeHook, chatCommand);
    }

    /** Runs {@code hook} with the new value after each change. */
    public ToggleSetting whenChanged(Consumer<Boolean> hook) {
        return new ToggleSetting(section, label, getter, setter, parent, notMemberMessage, hook, chatCommand);
    }

    /** Adds a command that flips it; {@code path} is space-separated, e.g. {@code "pf togglesounds"}. */
    public ToggleSetting command(String path, String pill, String feedbackPrefix) {
        ChatCommand command = new ChatCommand(List.of(path.split(" ")), pill, feedbackPrefix);
        return new ToggleSetting(section, label, getter, setter, parent, notMemberMessage, changeHook, command);
    }

    public boolean get(ModConfig config) {
        return getter.test(config);
    }

    public void set(ModConfig config, boolean on) {
        setter.accept(config, on);
    }

    public boolean isGuildOnly() {
        return notMemberMessage != null;
    }

    /**
     * Turns the setting on or off, saves the config and runs the change hook, then {@code onChanged}.
     * Turning on a guild-only setting checks membership first, which can finish later on the client
     * thread. Non-members get {@code onDenied} instead, and nothing changes.
     */
    public void change(ModConfig config, boolean on, Runnable onChanged, Runnable onDenied) {
        if (on && isGuildOnly()) {
            AvoAuthService.getInstance().runIfGuildMember(() -> apply(config, true, onChanged), onDenied);
        } else {
            apply(config, on, onChanged);
        }
    }

    private void apply(ModConfig config, boolean on, Runnable onChanged) {
        set(config, on);
        config.save();
        if (changeHook != null) {
            changeHook.accept(on);
        }
        if (onChanged != null) {
            onChanged.run();
        }
    }
}

