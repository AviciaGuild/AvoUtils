package info.avicia.avoutils.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.auth.AvoAuthService;
import info.avicia.avoutils.core.config.ModConfig;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingCommandsTest {

    private CommandDispatcher<FabricClientCommandSource> dispatcher;
    private FabricClientCommandSource source;
    private ModConfig config;

    @BeforeEach
    void setUp() {
        dispatcher = new CommandDispatcher<>();
        source = mock(FabricClientCommandSource.class);
        config = new ModConfig();
        SettingCommands.register(dispatcher, "avo");
    }

    @Test
    void commandsFlipTheirSetting() throws Exception {
        try (MockedStatic<AvoUtilsMod> mod = mockMod()) {
            dispatcher.execute("avo pf togglesounds", source);
            assertFalse(config.notificationSoundsEnabled);
            dispatcher.execute("avo pf togglesounds", source);
            assertTrue(config.notificationSoundsEnabled);

            dispatcher.execute("avo pf togglenotifs", source);
            assertFalse(config.newPartyNotifsEnabled);

            dispatcher.execute("avo emojis", source);
            assertFalse(config.emojiEnabled);
        }
    }

    @Test
    void guildOnlyCommandsTurnOnOnlyForMembers() throws Exception {
        try (MockedStatic<AvoUtilsMod> mod = mockMod();
             MockedStatic<AvoAuthService> auth = mockStatic(AvoAuthService.class)) {
            AvoAuthService authService = mock(AvoAuthService.class);
            auth.when(AvoAuthService::getInstance).thenReturn(authService);
            doCallRealMethod().when(authService).runIfGuildMember(any(), any());
            config.chatBridgeEnabled = false;
            config.guildStorageNotifsEnabled = false;

            when(authService.getCachedGuildMember()).thenReturn(false);
            dispatcher.execute("avo bridge", source);
            dispatcher.execute("avo storage", source);
            assertFalse(config.chatBridgeEnabled);
            assertFalse(config.guildStorageNotifsEnabled);

            when(authService.isGuildMember()).thenReturn(true);
            when(authService.getCachedGuildMember()).thenReturn(true);
            dispatcher.execute("avo bridge", source);
            dispatcher.execute("avo storage", source);
            assertTrue(config.chatBridgeEnabled);
            assertTrue(config.guildStorageNotifsEnabled);
        }
    }

    @Test
    void commandsDoNothingBeforeTheModLoads() throws Exception {
        dispatcher.execute("avo bridge", source);
        dispatcher.execute("avo pf togglesounds", source);

        verify(source, never()).sendFeedback(any());
    }

    @Test
    void aliasMirrorsOnlyTheCommandsUnderItsPrefix() {
        SettingCommands.registerAlias(dispatcher, "apf", "pf");

        CommandNode<FabricClientCommandSource> apf = dispatcher.getRoot().getChild("apf");
        Set<String> children = apf.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet());
        assertEquals(Set.of("togglenotifs", "togglesounds"), children);
        assertNull(apf.getCommand(), "the alias root gets no command of its own");
    }

    private MockedStatic<AvoUtilsMod> mockMod() {
        MockedStatic<AvoUtilsMod> mod = mockStatic(AvoUtilsMod.class);
        AvoUtilsMod instance = mock(AvoUtilsMod.class);
        when(instance.getConfig()).thenReturn(config);
        mod.when(AvoUtilsMod::getInstance).thenReturn(instance);
        return mod;
    }
}

