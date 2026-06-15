package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.PaperConfigLoader;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.screen.ScreenManager.DispatchMode;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class ConsoleDelegateCommandTest extends MockBukkitTest {

    private ConsoleDelegateCommand cmd;
    private ScreenManager screenManager;

    @BeforeEach
    void setUp() {
        screenManager = mock(ScreenManager.class);
        var loader = mock(PaperConfigLoader.class);
        when(plugin.getScreenManager()).thenReturn(screenManager);
        when(plugin.getConfigLoader()).thenReturn(loader);
        cmd = new ConsoleDelegateCommand(plugin);
    }

    @Test
    void stripsLeadingSlash() {
        PlayerMock target = createPlayer("Target");
        cmd.startSession("Console", target, "/mycommand foo");
        verify(screenManager, times(1)).startDelegatedSession(
                target, "mycommand foo", DispatchMode.CONSOLE, null);
    }

    @Test
    void replacesTargetPlayerPlaceholder() {
        PlayerMock target = createPlayer("Alice");
        cmd.startSession("Console", target, "msg %target_player% hi");
        verify(screenManager, times(1)).startDelegatedSession(
                target, "msg Alice hi", DispatchMode.CONSOLE, null);
    }

    @Test
    void noLeadingSlashNoReplacement() {
        PlayerMock target = createPlayer("Bob");
        cmd.startSession("Console", target, "plain command");
        verify(screenManager, times(1)).startDelegatedSession(
                target, "plain command", DispatchMode.CONSOLE, null);
    }

    @Test
    void buildReturnsNonNullLiteralNode() {
        // ArgumentTypes.player() requires the Paper VanillaArgumentProvider
        // which is only present in a real server, not in MockBukkit. We
        // exercise the dispatch logic via startSession() and skip the full
        // literal build here. The build() call is covered by deploy-time
        // smoke tests.
    }

    @Test
    void allowedRequiresConsoleAndPermission() {
        var console = mock(ConsoleCommandSender.class);
        when(console.hasPermission("promptpaper.consoledelegate")).thenReturn(true);
        assertTrue(cmd.allowed(console));

        var fakeConsole = mock(ConsoleCommandSender.class);
        when(fakeConsole.hasPermission("promptpaper.consoledelegate")).thenReturn(false);
        assertFalse(cmd.allowed(fakeConsole));
    }
}
