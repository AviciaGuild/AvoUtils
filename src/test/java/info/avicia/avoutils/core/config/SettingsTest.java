package info.avicia.avoutils.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    private static final Gson GSON = new Gson();

    @Test
    void sectionsListTheirSettingsInScreenOrder() {
        assertEquals(List.of(Settings.CHAT_BRIDGE), Settings.inSection(ConfigSection.CHAT_BRIDGE));
        assertEquals(List.of(Settings.EMOJIS, Settings.EMOJI_AUTOCOMPLETE), Settings.inSection(ConfigSection.EMOJIS));
        assertEquals(List.of(Settings.NEW_PARTY_NOTIFS, Settings.PARTY_SOUNDS),
                Settings.inSection(ConfigSection.PARTY_FINDER));
        assertEquals(List.of(Settings.STORAGE_NOTIFS, Settings.STORAGE_SOUNDS,
                        Settings.EMERALD_THRESHOLD, Settings.ASPECT_THRESHOLD),
                Settings.inSection(ConfigSection.GUILD_STORAGE));
        assertEquals(List.of(Settings.UPDATE_REMINDERS), Settings.inSection(ConfigSection.UPDATES));
    }

    @Test
    void everyDeclaredSettingIsListedOnce() throws IllegalAccessException {
        List<Setting> declared = new ArrayList<>();
        for (Field field : Settings.class.getFields()) {
            if (Setting.class.isAssignableFrom(field.getType())) {
                declared.add((Setting) field.get(null));
            }
        }

        assertEquals(declared.size(), Settings.ALL.size());
        assertEquals(Set.copyOf(declared), Set.copyOf(Settings.ALL));
    }

    @Test
    void eachSettingUsesItsOwnConfigField() {
        Set<String> fieldsUsed = new HashSet<>();
        for (Setting setting : Settings.ALL) {
            ModConfig config = new ModConfig();
            JsonObject before = GSON.toJsonTree(config).getAsJsonObject();

            switch (setting) {
                case ToggleSetting toggle -> {
                    boolean flipped = !toggle.get(config);
                    toggle.set(config, flipped);
                    assertEquals(flipped, toggle.get(config), setting.label());
                }
                case SliderSetting slider -> {
                    int value = slider.get(config) == slider.min() ? slider.max() : slider.min();
                    slider.set(config, value);
                    assertEquals(value, slider.get(config), setting.label());
                }
            }

            JsonObject after = GSON.toJsonTree(config).getAsJsonObject();
            List<String> changed = before.keySet().stream()
                    .filter(key -> !before.get(key).equals(after.get(key)))
                    .toList();
            assertEquals(1, changed.size(), setting.label() + " changed " + changed);
            assertTrue(fieldsUsed.add(changed.getFirst()), changed.getFirst() + " is used by two settings");
        }
    }

    @Test
    void toggleCommandsKeepTheirPaths() {
        Map<String, ToggleSetting> commands = new LinkedHashMap<>();
        for (ToggleSetting toggle : Settings.toggles()) {
            if (toggle.chatCommand() != null) {
                commands.put(String.join(" ", toggle.chatCommand().path()), toggle);
            }
        }

        assertEquals(Map.of(
                "bridge", Settings.CHAT_BRIDGE,
                "emojis", Settings.EMOJIS,
                "pf togglenotifs", Settings.NEW_PARTY_NOTIFS,
                "pf togglesounds", Settings.PARTY_SOUNDS,
                "storage", Settings.STORAGE_NOTIFS), commands);
    }

    @Test
    void childSettingsNeedTheirParentOn() {
        ModConfig config = new ModConfig();
        config.emojiEnabled = false;
        config.newPartyNotifsEnabled = false;
        assertFalse(Settings.EMOJI_AUTOCOMPLETE.isEditable(config, true));
        assertFalse(Settings.PARTY_SOUNDS.isEditable(config, true));

        config.emojiEnabled = true;
        config.newPartyNotifsEnabled = true;
        assertTrue(Settings.EMOJI_AUTOCOMPLETE.isEditable(config, true));
        assertTrue(Settings.PARTY_SOUNDS.isEditable(config, true));
    }

    @Test
    void guildOnlySettingsNeedGuildAccessToTurnOn() {
        ModConfig config = new ModConfig();
        config.chatBridgeEnabled = false;
        assertFalse(Settings.CHAT_BRIDGE.isEditable(config, false));
        assertTrue(Settings.CHAT_BRIDGE.isEditable(config, true));

        config.chatBridgeEnabled = true;
        assertTrue(Settings.CHAT_BRIDGE.isEditable(config, false), "anyone can turn it off");
    }

    @Test
    void storageControlsNeedNotificationsOnAndGuildAccess() {
        List<Setting> children = List.of(Settings.STORAGE_SOUNDS, Settings.EMERALD_THRESHOLD, Settings.ASPECT_THRESHOLD);
        ModConfig config = new ModConfig();
        for (Setting child : children) {
            assertTrue(child.isEditable(config, true), child.label());
            assertFalse(child.isEditable(config, false), child.label());
        }

        config.guildStorageNotifsEnabled = false;
        for (Setting child : children) {
            assertFalse(child.isEditable(config, true), child.label());
        }
    }

    @Test
    void slidersResetOutOfRangeValuesToTheirDefault() {
        ModConfig config = new ModConfig();
        config.guildStorageEmeraldThresholdPercent = 0;
        config.guildStorageAspectThresholdPercent = 101;
        Settings.ALL.forEach(setting -> setting.validate(config));
        assertEquals(90, config.guildStorageEmeraldThresholdPercent);
        assertEquals(90, config.guildStorageAspectThresholdPercent);

        config.guildStorageEmeraldThresholdPercent = 1;
        config.guildStorageAspectThresholdPercent = 100;
        Settings.ALL.forEach(setting -> setting.validate(config));
        assertEquals(1, config.guildStorageEmeraldThresholdPercent);
        assertEquals(100, config.guildStorageAspectThresholdPercent);
    }

    @Test
    void sliderDefaultsAreInRange() {
        for (Setting setting : Settings.ALL) {
            if (setting instanceof SliderSetting slider) {
                int value = slider.defaultValue();
                assertTrue(value >= slider.min() && value <= slider.max(), slider.label() + " default " + value);
            }
        }
    }
}

