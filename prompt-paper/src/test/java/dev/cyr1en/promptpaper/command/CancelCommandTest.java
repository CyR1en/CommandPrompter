package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CancelCommandTest extends MockBukkitTest {

    private CancelCommand cmd;
    private PromptEngine engine;

    @BeforeEach
    void setUp() {
        engine = mock(PromptEngine.class);
        when(plugin.getEngine()).thenReturn(engine);
        cmd = new CancelCommand(plugin);
    }

    @Test
    void nonPlayerSenderGetsErrorMessage() {
        var sender = mock(CommandSender.class);
        cmd.executeCancel(sender);
        verify(sender, times(1)).sendMessage(any(Component.class));
        verify(engine, never()).cancel(any(), any());
    }

    @Test
    void playerWithoutActiveSessionGetsNotice() {
        PlayerMock player = createPlayer("Alice");
        when(engine.hasActiveSession(player)).thenReturn(false);
        cmd.executeCancel(player);
        verify(engine, never()).cancel(any(), any());
    }

    @Test
    void playerWithActiveSessionIsCancelled() {
        PlayerMock player = createPlayer("Bob");
        when(engine.hasActiveSession(player)).thenReturn(true);
        cmd.executeCancel(player);
        verify(engine, times(1)).cancel(player, CancelReason.MANUAL);
    }

    @Test
    void buildReturnsNonNullLiteralNode() {
        assertNotNull(cmd.build());
    }

    @Test
    void allowedRequiresPromptPaperCancelPermission() {
        var sender = mock(CommandSender.class);
        when(sender.hasPermission("promptpaper.cancel")).thenReturn(false);
        assertFalse(cmd.allowed(sender));

        var withPerm = mock(CommandSender.class);
        when(withPerm.hasPermission("promptpaper.cancel")).thenReturn(true);
        assertTrue(cmd.allowed(withPerm));
    }
}
