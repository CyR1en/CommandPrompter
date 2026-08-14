package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CancelCommandTest extends MockBukkitTest {

    private CancelCommand cmd;
    private PromptEngine engine;
    private ScreenManager screenManager;

    @BeforeEach
    void setUp() {
        engine = mock(PromptEngine.class);
        when(plugin.getEngine()).thenReturn(engine);
        screenManager = mock(ScreenManager.class);
        when(plugin.getScreenManager()).thenReturn(screenManager);
        cmd = new CancelCommand(plugin);
    }

    @Test
    void nonPlayerSenderGetsErrorMessage() {
        var sender = mock(CommandSender.class);
        cmd.executeCancel(sender);
        verify(sender, times(1)).sendMessage(any(Component.class));
        verify(screenManager, never()).cancelAll(any());
        // Console/block senders keep context-free formatting (no PAPI context).
        verify(i18n).get("command.error.players_only");
    }

    @Test
    void playerWithoutActiveSessionGetsNotice() {
        PlayerMock player = createPlayer("Alice");
        when(engine.hasActiveSession(player)).thenReturn(false);
        cmd.executeCancel(player);
        verify(screenManager, never()).cancelAll(any());
        // The notice is sent directly to the player → player i18n context.
        verify(i18n).get(eq("command.cancel.no_active_prompt"), same(player));
    }

    @Test
    void playerWithActiveSessionIsCancelled() {
        when(config.showCancelled()).thenReturn(true);
        PlayerMock player = createPlayer("Bob");
        when(engine.hasActiveSession(player)).thenReturn(true);
        cmd.executeCancel(player);
        verify(screenManager, times(1)).cancelAll(player);
        // The cancellation feedback is sent directly to the player → player i18n context.
        verify(i18n).get(eq("prompt.cancelled"), same(player));
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

    @Test
    void nonOpPlayerCanCancelOwnSessionByDefault() {
        server.getPluginManager().addPermission(
                new Permission("promptpaper.cancel", PermissionDefault.TRUE));

        PlayerMock player = createPlayer("Casey");
        player.setOp(false);

        assertFalse(player.isOp());
        assertTrue(cmd.allowed(player),
                "promptpaper.cancel should allow non-op players by default");

        player.addAttachment(plugin, "promptpaper.cancel", false);
        assertFalse(cmd.allowed(player),
                "an explicit permission denial should still disable self-cancellation");
    }
}
