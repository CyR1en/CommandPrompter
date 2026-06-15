package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptpaper.MockBukkitTest;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class PromptRootCommandTest extends MockBukkitTest {

    @Test
    void buildReturnsLiteralWithSubcommands() {
        var root = new PromptRootCommand(plugin);
        var node = root.build();
        assertNotNull(node);
        // Top-level has one child literal per subcommand: reload, cancel, version.
        assertEquals(3, node.getChildren().size());
    }

    @Test
    void sendHelpSendsMessage() {
        var root = new PromptRootCommand(plugin);
        var sender = mock(CommandSender.class);
        root.sendHelp(sender);
        verify(sender, times(1)).sendMessage(any(Component.class));
    }

    @Test
    void allowedHasNoPermissionOrFilter() {
        var root = new PromptRootCommand(plugin);
        var sender = mock(CommandSender.class);
        assertTrue(root.allowed(sender));
    }

    @Test
    void buildAttachesCancelSubcommandWithoutAdminGate() {
        var root = new PromptRootCommand(plugin);
        var node = root.build();
        var cancelLiteral = node.getChildren().stream()
                .filter(c -> "cancel".equals(c.getName()))
                .findFirst()
                .orElseThrow();
        var cancelCommand = (CancelCommand) cancelLiteral.getCommand();
        var nonAdmin = mock(CommandSender.class);
        when(nonAdmin.hasPermission("promptpaper.cancel")).thenReturn(true);
        assertTrue(cancelCommand.allowed(nonAdmin));
    }
}
