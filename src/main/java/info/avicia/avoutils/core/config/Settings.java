package info.avicia.avoutils.core.config;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.features.emojis.EmojiFeature;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Every user-facing setting, declared once. The config screen builds its cards and rows from these
 * in declaration order, and each toggle with a command gets one under /avo and /avoutils.
 *
 * To add a setting, add a field to {@link ModConfig} (its initial value is the default) and
 * declare it here.
 */
public final class Settings {

    // Filled by declare() as the constants below initialize, so it must come first
    private static final List<Setting> DECLARED = new ArrayList<>();

    // ── Chat Bridge ──────────────────────────────────────────────────────

    public static final ToggleSetting CHAT_BRIDGE = declare(ToggleSetting.of(ConfigSection.CHAT_BRIDGE,
                    "Enabled (requires guild membership)",
                    config -> config.chatBridgeEnabled, (config, on) -> config.chatBridgeEnabled = on)
            .guildOnly("Chat bridge is unavailable: you are not in Avicia.")
            .command("bridge", "AvoBridge", "Chat bridge is now "));

    // ── Emojis ───────────────────────────────────────────────────────────

    public static final ToggleSetting EMOJIS = declare(ToggleSetting.of(ConfigSection.EMOJIS, "Enabled",
                    config -> config.emojiEnabled, (config, on) -> config.emojiEnabled = on)
            .whenChanged(Settings::loadEmojiPacks)
            .command("emojis", "AvoUtils", "Emojis are now "));

    public static final ToggleSetting EMOJI_AUTOCOMPLETE = declare(ToggleSetting.of(ConfigSection.EMOJIS,
                    "Emoji Autocomplete",
                    config -> config.emojiAutocompleteEnabled, (config, on) -> config.emojiAutocompleteEnabled = on)
            .requires(EMOJIS));

    // ── Party Finder ─────────────────────────────────────────────────────

    public static final ToggleSetting NEW_PARTY_NOTIFS = declare(ToggleSetting.of(ConfigSection.PARTY_FINDER,
                    "New Party Notifications",
                    config -> config.newPartyNotifsEnabled, (config, on) -> config.newPartyNotifsEnabled = on)
            .command("pf togglenotifs", "AvoUtils", "New party notifications are now "));

    public static final ToggleSetting PARTY_SOUNDS = declare(ToggleSetting.of(ConfigSection.PARTY_FINDER,
                    "Notification Sounds",
                    config -> config.notificationSoundsEnabled, (config, on) -> config.notificationSoundsEnabled = on)
            .requires(NEW_PARTY_NOTIFS)
            .command("pf togglesounds", "AvoUtils", "Notification sounds are now "));

    // ── Guild Rewards Storage ────────────────────────────────────────────

    public static final ToggleSetting STORAGE_NOTIFS = declare(ToggleSetting.of(ConfigSection.GUILD_STORAGE,
                    "Notifications (requires guild membership)",
                    config -> config.guildStorageNotifsEnabled, (config, on) -> config.guildStorageNotifsEnabled = on)
            .guildOnly("Storage notifications are unavailable: you are not in Avicia.")
            .command("storage", "AvoUtils", "Storage threshold notifications are now "));

    public static final ToggleSetting STORAGE_SOUNDS = declare(ToggleSetting.of(ConfigSection.GUILD_STORAGE,
                    "Notification Sounds",
                    config -> config.guildStorageNotifSoundsEnabled, (config, on) -> config.guildStorageNotifSoundsEnabled = on)
            .requires(STORAGE_NOTIFS));

    public static final SliderSetting EMERALD_THRESHOLD = declare(SliderSetting.of(ConfigSection.GUILD_STORAGE,
                    "Emerald threshold", 1, 100, "%",
                    config -> config.guildStorageEmeraldThresholdPercent,
                    (config, value) -> config.guildStorageEmeraldThresholdPercent = value)
            .requires(STORAGE_NOTIFS));

    public static final SliderSetting ASPECT_THRESHOLD = declare(SliderSetting.of(ConfigSection.GUILD_STORAGE,
                    "Aspect threshold", 1, 100, "%",
                    config -> config.guildStorageAspectThresholdPercent,
                    (config, value) -> config.guildStorageAspectThresholdPercent = value)
            .requires(STORAGE_NOTIFS));

    // ── Updates ──────────────────────────────────────────────────────────

    public static final ToggleSetting UPDATE_REMINDERS = declare(ToggleSetting.of(ConfigSection.UPDATES,
            "Update Reminders",
            config -> config.updateRemindersEnabled, (config, on) -> config.updateRemindersEnabled = on));

    /** Every setting, in config screen order. */
    public static final List<Setting> ALL = List.copyOf(DECLARED);

    private static final Map<ConfigSection, List<Setting>> BY_SECTION = ALL.stream().collect(Collectors.groupingBy(
            Setting::section, () -> new EnumMap<>(ConfigSection.class), Collectors.toUnmodifiableList()));

    private Settings() {
    }

    /** The settings in a section's card, top to bottom. */
    public static List<Setting> inSection(ConfigSection section) {
        return BY_SECTION.getOrDefault(section, List.of());
    }

    /** All toggles, in config screen order. */
    public static List<ToggleSetting> toggles() {
        return ALL.stream().filter(ToggleSetting.class::isInstance).map(ToggleSetting.class::cast).toList();
    }

    private static <T extends Setting> T declare(T setting) {
        DECLARED.add(setting);
        return setting;
    }

    private static void loadEmojiPacks(boolean on) {
        AvoUtilsMod mod = AvoUtilsMod.getInstance();
        EmojiFeature emojis = mod != null ? mod.getFeature(EmojiFeature.class) : null;
        if (on && emojis != null) {
            emojis.ensurePacksLoaded();
        }
    }
}

