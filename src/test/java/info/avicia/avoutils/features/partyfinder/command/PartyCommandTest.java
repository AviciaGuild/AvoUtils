package info.avicia.avoutils.features.partyfinder.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.tree.CommandNode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PartyCommandTest {

    @Test
    void apfHasTheSameSubcommandsAsAvoPf() {
        CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();
        PartyCommand.register(dispatcher);

        CommandNode<FabricClientCommandSource> apf = dispatcher.getRoot().getChild("apf");
        Set<String> children = apf.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet());
        assertEquals(Set.of("togglenotifs", "togglesounds", "join"), children);

        FabricClientCommandSource source = mock(FabricClientCommandSource.class);
        for (String command : List.of("apf", "apf togglenotifs", "apf togglesounds", "apf join Steve")) {
            ParseResults<FabricClientCommandSource> parse = dispatcher.parse(command, source);
            assertTrue(parse.getExceptions().isEmpty(), command + ": " + parse.getExceptions());
            assertNotNull(parse.getContext().getCommand(), command + " should run something");
        }
    }
}

