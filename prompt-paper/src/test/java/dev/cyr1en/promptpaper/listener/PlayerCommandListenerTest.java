package dev.cyr1en.promptpaper.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.PaperConfigLoader;
import dev.cyr1en.promptpaper.engine.PromptEngine;
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
    private PromptEngine engine;

    @BeforeEach
    void setUpListener() {
        screenManager = mock(ScreenManager.class);
        plugin = mock(CommandPrompter.class);
        logger = mock(PluginLogger.class);
        engine = mock(PromptEngine.class);
        var loader = mock(PaperConfigLoader.class);
        when(plugin.getConfigLoader()).thenReturn(loader);
        when(plugin.getPluginLogger()).thenReturn(logger);
        when(plugin.getEngine()).thenReturn(engine);
        var config = mock(dev.cyr1en.promptpaper.config.CommandPrompterConfig.class);
        when(loader.getConfig()).thenReturn(config);
        when(config.ignoredCommands()).thenReturn(java.util.List.of());
        when(config.allowedWhileInPrompt()).thenReturn(java.util.List.of());
        when(config.enablePermission()).thenReturn(false);
        when(screenManager.hasActiveScreen(any())).thenReturn(true);
        // Default: command has no tag form.
        when(engine.commandHasTagForm(anyString())).thenReturn(false);
        when(engine.hasPresetReferences(anyString())).thenReturn(false);
        listener = new PlayerCommandListener(plugin, screenManager, engine);
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

    // --- Scope 4: preset tag fail-fast ---

    @Test
    void presetPromptTagCancelsEventEvenWhenNoSessionStarts() {
        // The command has a preset prompt tag. The fail-fast path
        // (engine returns empty) means no screen is started, but the
        // event must STILL be cancelled so the literal <@id> markup is
        // never dispatched.
        when(engine.commandHasTagForm(anyString())).thenReturn(true);
        when(engine.hasPresetReferences(anyString())).thenReturn(true);
        when(screenManager.hasActiveScreen(any())).thenReturn(false);
        var player = createPlayer();
        var event = new PlayerCommandPreprocessEvent(player, "/cmd <@missing>");
        listener.onPlayerCommand(event);

        assertTrue(event.isCancelled());
        verify(screenManager).startSession(eq(player), eq("cmd <@missing>"));
    }

    @Test
    void presetPostCommandTagCancelsEvent() {
        // A command with only a preset post-command (no prompt tags) has
        // no session, but the listener must still cancel so the literal
        // <!@id> markup is never dispatched.
        when(engine.commandHasTagForm(anyString())).thenReturn(true);
        when(engine.hasPresetReferences(anyString())).thenReturn(true);
        when(screenManager.hasActiveScreen(any())).thenReturn(false);
        var player = createPlayer();
        var event = new PlayerCommandPreprocessEvent(player, "/cmd <!@missing_log>");
        listener.onPlayerCommand(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void legacyInlineTagsDoNotCancelWhenNoScreen() {
        // Legacy inline tags (<a:why>, <!log>) without presets must pass
        // through unchanged — same as the pre-Scope-4 behavior. The
        // listener should NOT cancel when the command has tag form
        // but no preset references AND no session starts.
        when(engine.commandHasTagForm(anyString())).thenReturn(true);
        when(engine.hasPresetReferences(anyString())).thenReturn(false);
        when(screenManager.hasActiveScreen(any())).thenReturn(false);
        var player = createPlayer();
        var event = new PlayerCommandPreprocessEvent(player, "/cmd <a:why>");
        listener.onPlayerCommand(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void presetCommandNotCancelledWhenPermissionDenied() {
        // When the player lacks the use permission, the engine returns
        // empty (no fail-fast, no session). The listener must not
        // cancel — the command should dispatch normally so the
        // permission system can surface the right error.
        when(engine.commandHasTagForm(anyString())).thenReturn(true);
        when(engine.hasPresetReferences(anyString())).thenReturn(true);
        when(screenManager.hasActiveScreen(any())).thenReturn(false);
        var config = plugin.getConfigLoader().getConfig();
        when(config.enablePermission()).thenReturn(true);
        // Use a Mockito mock Player so we can stub hasPermission cleanly.
        var player = mock(org.bukkit.entity.Player.class);
        when(player.getServer()).thenReturn(server);
        when(player.hasPermission("promptpaper.use")).thenReturn(false);
        var event = new PlayerCommandPreprocessEvent(player, "/cmd <@my_prompt>");
        listener.onPlayerCommand(event);

        assertFalse(event.isCancelled(),
                "Permission-denied commands must not be cancelled by the listener");
    }
}
