package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.CommandPrompter;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;

class PromptCommandTest extends MockBukkitTest {

    @Test
    void allowedPassesWithNoPermissionAndNoFilter() {
        var cmd = new TestCommand(plugin, "x", null, null, "desc", List.of());
        assertTrue(cmd.allowed(mock(CommandSender.class)));
    }

    @Test
    void allowedFailsWhenPermissionMissing() {
        var sender = mock(CommandSender.class);
        when(sender.hasPermission("promptpaper.test")).thenReturn(false);
        var cmd = new TestCommand(plugin, "x", "promptpaper.test", null, "desc", List.of());
        assertFalse(cmd.allowed(sender));
    }

    @Test
    void allowedPassesWhenPermissionPresent() {
        var sender = mock(CommandSender.class);
        when(sender.hasPermission("promptpaper.test")).thenReturn(true);
        var cmd = new TestCommand(plugin, "x", "promptpaper.test", null, "desc", List.of());
        assertTrue(cmd.allowed(sender));
    }

    @Test
    void allowedFailsWhenSenderFilterMismatched() {
        var sender = mock(CommandSender.class);
        var cmd = new TestCommand(plugin, "x", null, ConsoleCommandSender.class, "desc", List.of());
        assertFalse(cmd.allowed(sender));
    }

    @Test
    void allowedPassesWhenSenderFilterMatches() {
        var sender = mock(ConsoleCommandSender.class);
        var cmd = new TestCommand(plugin, "x", null, ConsoleCommandSender.class, "desc", List.of());
        assertTrue(cmd.allowed(sender));
    }

    @Test
    void allowedCombinesBothChecks() {
        var sender = mock(CommandSender.class);
        when(sender.hasPermission("promptpaper.test")).thenReturn(false);
        var cmd = new TestCommand(plugin, "x", "promptpaper.test", ConsoleCommandSender.class,
                "desc", List.of());
        assertFalse(cmd.allowed(sender));
    }

    @Test
    void nameDescriptionAliasesExposed() {
        var cmd = new TestCommand(plugin, "thing", null, null, "the description", List.of("t", "th"));
        assertEquals("thing", cmd.name());
        assertEquals("the description", cmd.description());
        assertEquals(List.of("t", "th"), cmd.aliases());
    }

    @Test
    void buildReturnsLiteralCommandNode() {
        var cmd = new TestCommand(plugin, "thing", null, null, "desc", List.of());
        assertNotNull(cmd.build());
    }

    /** Concrete PromptCommand for testing the abstract base. */
    private static final class TestCommand extends PromptCommand {
        TestCommand(CommandPrompter plugin, String name, String permission,
                    Class<? extends CommandSender> senderFilter, String description,
                    List<String> aliases) {
            super(plugin, name, permission, senderFilter, description, aliases);
        }

        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return Commands.literal(name())
                    .requires(src -> allowed(src.getSender()))
                    .build();
        }
    }
}
