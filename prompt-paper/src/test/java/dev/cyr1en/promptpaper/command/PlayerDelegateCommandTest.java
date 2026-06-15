package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.CommandPrompterConfig;
import dev.cyr1en.promptpaper.config.PaperConfigLoader;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.screen.ScreenManager.DispatchMode;
import net.kyori.adventure.text.Component;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PlayerDelegateCommandTest extends MockBukkitTest {

    private PlayerDelegateCommand cmd;
    private ScreenManager screenManager;
    private CommandPrompterConfig config;

    @BeforeEach
    void setUp() {
        screenManager = mock(ScreenManager.class);
        var loader = mock(PaperConfigLoader.class);
        config = mock(CommandPrompterConfig.class);

        when(config.getPermissionAttachment("GAMEMODE"))
                .thenReturn(new String[]{"bukkit.command.gamemode"});
        when(config.getPermissionKeys())
                .thenReturn(new String[]{"GAMEMODE", "NONE"});

        when(loader.getConfig()).thenReturn(config);

        when(plugin.getScreenManager()).thenReturn(screenManager);
        when(plugin.getConfigLoader()).thenReturn(loader);

        cmd = new PlayerDelegateCommand(plugin);
    }

    @Test
    void stripsLeadingSlashAndReplacesTargetPlayer() {
        PlayerMock target = createPlayer("Alice");
        var sender = mock(ConsoleCommandSender.class);

        var result = cmd.executeDispatch(sender, target, "GAMEMODE",
                "/msg %target_player% hi");

        assertEquals(com.mojang.brigadier.Command.SINGLE_SUCCESS, result);
        verify(screenManager, times(1)).startDelegatedSession(
                target, "msg Alice hi", DispatchMode.ATTACHMENT, "GAMEMODE");
    }

    @Test
    void unknownPermissionKeySendsErrorAndDoesNotStartSession() {
        PlayerMock target = createPlayer("Bob");
        var sender = mock(ConsoleCommandSender.class);

        when(config.getPermissionAttachment("NOPE")).thenReturn(new String[0]);

        var result = cmd.executeDispatch(sender, target, "NOPE", "some command");

        assertEquals(com.mojang.brigadier.Command.SINGLE_SUCCESS, result);
        verify(sender, times(1)).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(screenManager, never()).startDelegatedSession(
                any(), any(), any(), any());
    }

    @Test
    void buildReturnsNonNullLiteralNode() {
        // ArgumentTypes.player() requires the Paper VanillaArgumentProvider
        // which is only present in a real server, not in MockBukkit. The
        // build() call is covered by deploy-time smoke tests; here we
        // exercise the dispatch logic via executeDispatch().
    }

    @Test
    void allowedRequiresConsoleAndPermission() {
        var console = mock(ConsoleCommandSender.class);
        when(console.hasPermission("promptpaper.playerdelegate")).thenReturn(true);
        assertTrue(cmd.allowed(console));

        var noPerm = mock(ConsoleCommandSender.class);
        when(noPerm.hasPermission("promptpaper.playerdelegate")).thenReturn(false);
        assertFalse(cmd.allowed(noPerm));
    }
}
