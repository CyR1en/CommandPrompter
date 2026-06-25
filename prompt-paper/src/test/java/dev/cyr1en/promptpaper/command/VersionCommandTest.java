package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptpaper.MockBukkitTest;
import io.papermc.paper.plugin.configuration.PluginMeta;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VersionCommandTest extends MockBukkitTest {

    private VersionCommand cmd;

    @BeforeEach
    void setUp() {
        var meta = mock(PluginMeta.class);
        when(meta.getVersion()).thenReturn("3.0.1-test");
        when(plugin.getPluginMeta()).thenReturn(meta);

        cmd = new VersionCommand(plugin);
    }

    @Test
    void sendsVersionToSender() {
        var sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("Alice");
        cmd.sendVersion(sender);
        verify(sender, times(1)).sendMessage(any(Component.class));
    }

    @Test
    void buildReturnsNonNullLiteralNode() {
        assertNotNull(cmd.build());
    }

    @Test
    void allowedRequiresPermission() {
        var noPerm = mock(CommandSender.class);
        when(noPerm.hasPermission("promptpaper.version")).thenReturn(false);
        assertFalse(cmd.allowed(noPerm));

        var withPerm = mock(CommandSender.class);
        when(withPerm.hasPermission("promptpaper.version")).thenReturn(true);
        assertTrue(cmd.allowed(withPerm));
    }
}
