package info.avicia.avoutils.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AvoCommandsTest {

    private CommandDispatcher<FabricClientCommandSource> dispatcher;
    private FabricClientCommandSource source;

    @BeforeEach
    void setUp() {
        dispatcher = new CommandDispatcher<>();
        source = mock(FabricClientCommandSource.class);
        AvoCommands.register(dispatcher);
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

        Set<String> expectedSubcommands = Set.of("config", "bridge", "storage", "emojis", "anni", "pf");
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
                "avoutils pf join PlayerName"
        };

        for (String cmd : commandLines) {
            ParseResults<FabricClientCommandSource> parse = dispatcher.parse(cmd, source);
            assertTrue(parse.getExceptions().isEmpty(),
                    "Expected no parse exceptions for '" + cmd + "', but got: " + parse.getExceptions());
            assertNotNull(parse.getContext().getCommand(),
                    "Expected executable command for '" + cmd + "'");
        }
    }
}

