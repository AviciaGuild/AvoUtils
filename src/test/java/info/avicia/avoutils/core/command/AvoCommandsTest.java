package info.avicia.avoutils.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import net.minecraft.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AvoCommandsTest {

    private CommandDispatcher<FabricClientCommandSource> dispatcher;
    private FabricClientCommandSource source;

    @BeforeEach
    void setUp() {
        dispatcher = new CommandDispatcher<>();
        source = mock(FabricClientCommandSource.class);
        AvoCommands.register(dispatcher);
    }

    @AfterEach
    void tearDown() {
        WynncraftServerPolicy.setScopeOverride(null);
    }

    @Test
    void testCommandRootsContainsAvoAndAvoutils() {
        assertEquals(List.of("avo", "avoutils"), AvoCommands.COMMAND_ROOTS);
    }

    @Test
    void testBothRootsAreRegistered() {
        CommandNode<FabricClientCommandSource> avoNode = dispatcher.getRoot().getChild("avo");
        CommandNode<FabricClientCommandSource> avoutilsNode = dispatcher.getRoot().getChild("avoutils");

        assertNotNull(avoNode, "/avo should be registered");
        assertNotNull(avoutilsNode, "/avoutils should be registered");
    }

    @Test
    void testSubcommandEquivalence() {
        CommandNode<FabricClientCommandSource> avoNode = dispatcher.getRoot().getChild("avo");
        CommandNode<FabricClientCommandSource> avoutilsNode = dispatcher.getRoot().getChild("avoutils");

        assertNotNull(avoNode);
        assertNotNull(avoutilsNode);

        Set<String> avoChildren = avoNode.getChildren().stream()
                .map(CommandNode::getName)
                .collect(Collectors.toSet());
        Set<String> avoutilsChildren = avoutilsNode.getChildren().stream()
                .map(CommandNode::getName)
                .collect(Collectors.toSet());

        Set<String> expectedSubcommands = Set.of("config", "bridge", "storage", "emojis", "anni", "pf", "update");
        assertEquals(expectedSubcommands, avoChildren);
        assertEquals(expectedSubcommands, avoutilsChildren);
        assertEquals(avoChildren, avoutilsChildren);

        // Check emojis children
        Set<String> avoEmojiChildren = avoNode.getChild("emojis").getChildren().stream()
                .map(CommandNode::getName)
                .collect(Collectors.toSet());
        Set<String> avoutilsEmojiChildren = avoutilsNode.getChild("emojis").getChildren().stream()
                .map(CommandNode::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("reload", "update"), avoEmojiChildren);
        assertEquals(Set.of("reload", "update"), avoutilsEmojiChildren);

        // Check pf children
        Set<String> avoPfChildren = avoNode.getChild("pf").getChildren().stream()
                .map(CommandNode::getName)
                .collect(Collectors.toSet());
        Set<String> avoutilsPfChildren = avoutilsNode.getChild("pf").getChildren().stream()
                .map(CommandNode::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("togglenotifs", "togglesounds", "join"), avoPfChildren);
        assertEquals(Set.of("togglenotifs", "togglesounds", "join"), avoutilsPfChildren);

        // Check update children
        Set<String> avoUpdateChildren = avoNode.getChild("update").getChildren().stream()
                .map(CommandNode::getName)
                .collect(Collectors.toSet());
        Set<String> avoutilsUpdateChildren = avoutilsNode.getChild("update").getChildren().stream()
                .map(CommandNode::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("check", "download", "restart"), avoUpdateChildren);
        assertEquals(Set.of("check", "download", "restart"), avoutilsUpdateChildren);
    }

    @Test
    void testParsingAvoutilsCommands() {
        String[] commandLines = {
                "avoutils",
                "avoutils config",
                "avoutils bridge",
                "avoutils storage",
                "avoutils emojis",
                "avoutils emojis reload",
                "avoutils emojis update",
                "avoutils anni",
                "avoutils pf",
                "avoutils pf togglenotifs",
                "avoutils pf togglesounds",
                "avoutils pf join PlayerName",
                "avoutils update",
                "avoutils update check",
                "avoutils update download",
                "avoutils update restart"
        };

        for (String cmd : commandLines) {
            ParseResults<FabricClientCommandSource> parse = dispatcher.parse(cmd, source);
            assertTrue(parse.getExceptions().isEmpty(),
                    "Expected no parse exceptions for '" + cmd + "', but got: " + parse.getExceptions());
            assertNotNull(parse.getContext().getCommand(),
                    "Expected executable command for '" + cmd + "'");
        }
    }

    @Test
    void testActionCommandsBlockedWhenNotOnWynncraft() throws Exception {
        WynncraftServerPolicy.setScopeOverride(() -> WynncraftServerPolicy.Scope.BLOCKED);

        dispatcher.execute("avo pf", source);
        ArgumentCaptor<Text> captor = ArgumentCaptor.forClass(Text.class);
        verify(source).sendFeedback(captor.capture());
        assertTrue(captor.getValue().getString().contains(WynncraftServerPolicy.NOT_ON_WYNCRAFT_MESSAGE));

        reset(source);
        dispatcher.execute("avo anni", source);
        verify(source).sendFeedback(captor.capture());
        assertTrue(captor.getValue().getString().contains(WynncraftServerPolicy.NOT_ON_WYNCRAFT_MESSAGE));

        reset(source);
        dispatcher.execute("avo update", source);
        verify(source).sendFeedback(captor.capture());
        assertTrue(captor.getValue().getString().contains(WynncraftServerPolicy.NOT_ON_WYNCRAFT_MESSAGE));

        reset(source);
        dispatcher.execute("avo emojis reload", source);
        verify(source).sendFeedback(captor.capture());
        assertTrue(captor.getValue().getString().contains(WynncraftServerPolicy.NOT_ON_WYNCRAFT_MESSAGE));
    }

    @Test
    void testNetworkingCommandsBlockedOnBeta() throws Exception {
        WynncraftServerPolicy.setScopeOverride(() -> WynncraftServerPolicy.Scope.BETA);

        dispatcher.execute("avo pf", source);
        ArgumentCaptor<Text> captor = ArgumentCaptor.forClass(Text.class);
        verify(source).sendFeedback(captor.capture());
        assertTrue(captor.getValue().getString().contains(WynncraftServerPolicy.BETA_NETWORKING_BLOCKED_MESSAGE));

        reset(source);
        dispatcher.execute("avo anni", source);
        verify(source).sendFeedback(captor.capture());
        assertTrue(captor.getValue().getString().contains(WynncraftServerPolicy.BETA_NETWORKING_BLOCKED_MESSAGE));

        reset(source);
        dispatcher.execute("avo update", source);
        verify(source).sendFeedback(captor.capture());
        assertTrue(captor.getValue().getString().contains(WynncraftServerPolicy.BETA_NETWORKING_BLOCKED_MESSAGE));
    }

    @Test
    void testSettingCommandsAllowedWhenOutsideWynncraftOrBeta() throws Exception {
        WynncraftServerPolicy.setScopeOverride(() -> WynncraftServerPolicy.Scope.BLOCKED);

        // Setting toggle commands should not receive server policy blocked message
        dispatcher.execute("avo bridge", source);
        verify(source, never()).sendFeedback(any());

        dispatcher.execute("avo storage", source);
        verify(source, never()).sendFeedback(any());

        dispatcher.execute("avo emojis", source);
        verify(source, never()).sendFeedback(any());

        WynncraftServerPolicy.setScopeOverride(() -> WynncraftServerPolicy.Scope.BETA);

        dispatcher.execute("avo bridge", source);
        verify(source, never()).sendFeedback(any());

        dispatcher.execute("avo storage", source);
        verify(source, never()).sendFeedback(any());

        dispatcher.execute("avo emojis", source);
        verify(source, never()).sendFeedback(any());
    }
}

