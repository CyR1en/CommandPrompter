package dev.cyr1en.promptpaper.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.PaperConfigLoader;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.util.PluginLogger;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerCommandListenerTest extends MockBukkitTest {

    private ScreenManager screenManager;
    private PlayerCommandListener listener;
    private CommandPrompter plugin;
    private PluginLogger logger;

    @BeforeEach
    void setUpListener() {
        screenManager = mock(ScreenManager.class);
        plugin = mock(CommandPrompter.class);
        logger = mock(PluginLogger.class);
        var loader = mock(PaperConfigLoader.class);
        when(plugin.getConfigLoader()).thenReturn(loader);
        when(plugin.getPluginLogger()).thenReturn(logger);
        var config = mock(dev.cyr1en.promptpaper.config.CommandPrompterConfig.class);
        when(loader.getConfig()).thenReturn(config);
        when(config.ignoredCommands()).thenReturn(java.util.List.of());
        when(config.allowedWhileInPrompt()).thenReturn(java.util.List.of());
        when(screenManager.hasActiveScreen(any())).thenReturn(true);
        listener = new PlayerCommandListener(plugin, screenManager);
    }

    @Test
    void commandInterceptionCancelsEventWhenScreenActive() {
        var player = createPlayer();
        var event = new PlayerCommandPreprocessEvent(player, "/some command");
        listener.onPlayerCommand(event);

        assertTrue(event.isCancelled());
        verify(screenManager).startSession(eq(player), eq("some command"));
    }

    @Test
    void commandPrompterCommandIsNotIntercepted() {
        var player = createPlayer();
        var event = new PlayerCommandPreprocessEvent(player, "/commandprompter reload");
        listener.onPlayerCommand(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void cmdpAliasIsNotIntercepted() {
        var player = createPlayer();
        var event = new PlayerCommandPreprocessEvent(player, "/cmdp cancel");
        listener.onPlayerCommand(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void cancelledEventIsSkipped() {
        var player = createPlayer();
        var event = new PlayerCommandPreprocessEvent(player, "/some command");
        event.setCancelled(true);
        listener.onPlayerCommand(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void normalCommandDoesNotInterceptWhenNoActiveScreen() {
        when(screenManager.hasActiveScreen(any())).thenReturn(false);
        var player = createPlayer();
        var event = new PlayerCommandPreprocessEvent(player, "/some command");
        listener.onPlayerCommand(event);

        assertFalse(event.isCancelled());
    }
}
