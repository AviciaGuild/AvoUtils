package info.avicia.avoutils.core.config;

import info.avicia.avoutils.core.auth.AvoAuthService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ToggleSettingTest {

    private static final List<ToggleSetting> GUILD_ONLY = List.of(Settings.CHAT_BRIDGE, Settings.STORAGE_NOTIFS);

    @Test
    void changeSavesTheValueThenRunsTheHookAndCallback() {
        List<String> events = new ArrayList<>();
        ToggleSetting setting = ToggleSetting.of(ConfigSection.UPDATES, "Test",
                        config -> config.updateRemindersEnabled, (config, on) -> config.updateRemindersEnabled = on)
                .whenChanged(on -> events.add("hook " + on));
        ModConfig config = new ModConfig();

        setting.change(config, false, () -> events.add("changed"), () -> events.add("denied"));

        assertFalse(config.updateRemindersEnabled);
        assertEquals(List.of("hook false", "changed"), events);
    }

    @Test
    void ordinarySettingsNeverCheckMembership() {
        withAuth(false, authService -> {
            ModConfig config = new ModConfig();
            config.emojiAutocompleteEnabled = false;

            Settings.EMOJI_AUTOCOMPLETE.change(config, true, null, () -> fail("denied"));

            assertTrue(config.emojiAutocompleteEnabled);
            verifyNoInteractions(authService);
        });
    }

    @Test
    void membersCanTurnOnGuildOnlySettings() {
        withAuth(true, authService -> {
            for (ToggleSetting setting : GUILD_ONLY) {
                ModConfig config = new ModConfig();
                setting.set(config, false);

                setting.change(config, true, null, () -> fail("denied"));

                assertTrue(setting.get(config), setting.label());
            }
        });
    }

    @Test
    void nonMembersAreDeniedAndTheSettingStaysOff() {
        withAuth(false, authService -> {
            for (ToggleSetting setting : GUILD_ONLY) {
                ModConfig config = new ModConfig();
                setting.set(config, false);
                List<String> events = new ArrayList<>();

                setting.change(config, true, () -> events.add("changed"), () -> events.add("denied"));

                assertFalse(setting.get(config), setting.label());
                assertEquals(List.of("denied"), events);
            }
        });
    }

    @Test
    void unknownMembershipIsResolvedBeforeTurningOn() {
        withAuth(null, authService -> {
            when(authService.resolveGuildMembership()).thenReturn(CompletableFuture.completedFuture(true));
            ModConfig config = new ModConfig();
            config.chatBridgeEnabled = false;

            Settings.CHAT_BRIDGE.change(config, true, null, () -> fail("denied"));

            verify(authService).resolveGuildMembership();
            assertTrue(config.chatBridgeEnabled);
        });
    }

    @Test
    void anyoneCanTurnOffGuildOnlySettings() {
        withAuth(false, authService -> {
            for (ToggleSetting setting : GUILD_ONLY) {
                ModConfig config = new ModConfig();

                setting.change(config, false, null, () -> fail("denied"));

                assertFalse(setting.get(config), setting.label());
            }
            verify(authService, never()).runIfGuildMember(any(), any());
        });
    }

    /**
     * Runs {@code body} with guild membership cached as {@code member}; null means not known yet.
     */
    private static void withAuth(Boolean member, Consumer<AvoAuthService> body) {
        try (MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            doCallRealMethod().when(authService).runIfGuildMember(any(), any());
            when(authService.isGuildMember()).thenReturn(Boolean.TRUE.equals(member));
            when(authService.getCachedGuildMember()).thenReturn(member);
            body.accept(authService);
        }
    }
}

