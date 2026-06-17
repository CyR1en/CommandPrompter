package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DelegateCommandsIntegrationTest extends MockBukkitTest {

    private PromptEngine engine;
    private PromptFactory factory;
    private ScreenManager screenManager;

    @BeforeEach
    void setUpDelegates() {
        var promptConfig = org.mockito.Mockito.mock(dev.cyr1en.promptpaper.config.PromptConfig.class);
        when(promptConfig.getScreenMappings()).thenReturn(Map.of("", ScreenType.CHAT));
        lenient().when(promptConfig.sendCancelText()).thenReturn(false);
        lenient().when(promptConfig.responseListenerPriority()).thenReturn("LOWEST");
        when(configLoader.getPromptConfig()).thenReturn(promptConfig);

        engine = new PromptEngine(plugin, scheduler);
        factory = new PromptFactory(plugin);
        screenManager = new ScreenManager(plugin, engine, factory, scheduler);
    }

    @Test
    void consoleDelegateWithNoPromptsDispatchesDirectly() {
        var player = createPlayer();
        screenManager.startDelegatedSession(player, "/cmd no prompts",
                ScreenManager.DispatchMode.CONSOLE, null);
        assertFalse(screenManager.hasActiveScreen(player));
    }

    @Test
    void consoleDelegateWithPromptsCreatesScreen() {
        var player = createPlayer();
        screenManager.startDelegatedSession(player, "/cmd <test>",
                ScreenManager.DispatchMode.CONSOLE, null);
        assertTrue(screenManager.hasActiveScreen(player));
    }

    @Test
    void playerDelegateWithNoPromptsDispatchesDirectly() {
        var player = createPlayer();
        screenManager.startDelegatedSession(player, "/cmd no prompts",
                ScreenManager.DispatchMode.ATTACHMENT, "test-key");
        assertFalse(screenManager.hasActiveScreen(player));
    }

    @Test
    void playerDelegateWithPromptsCreatesScreen() {
        var player = createPlayer();
        screenManager.startDelegatedSession(player, "/cmd <test>",
                ScreenManager.DispatchMode.ATTACHMENT, "test-key");
        assertTrue(screenManager.hasActiveScreen(player));
    }

    @Test
    void normalDelegateWithPromptsCreatesScreen() {
        var player = createPlayer();
        screenManager.startDelegatedSession(player, "/cmd <test>",
                ScreenManager.DispatchMode.NORMAL, null);
        assertTrue(screenManager.hasActiveScreen(player));
    }

    @Test
    void delegatedSessionCompletesWithChatInput() {
        var player = createPlayer();
        screenManager.startDelegatedSession(player, "/cmd <test>",
                ScreenManager.DispatchMode.CONSOLE, null);
        assertTrue(screenManager.hasChatScreen(player));

        screenManager.handleChatInput(player, "myValue");
        assertFalse(screenManager.hasActiveScreen(player));
        assertFalse(engine.hasActiveSession(player));
    }
}
