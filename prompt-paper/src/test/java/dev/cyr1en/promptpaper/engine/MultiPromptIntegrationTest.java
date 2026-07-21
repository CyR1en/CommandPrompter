package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiPromptIntegrationTest extends MockBukkitTest {

    private PromptEngine engine;
    private PromptFactory factory;
    private ScreenManager screenManager;

    @BeforeEach
    void setUpIntegration() {
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
    void threePromptCommandCyclesThroughAllPrompts() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <First> <Second> <Third>");

        assertTrue(screenManager.hasActiveScreen(player));
        assertTrue(screenManager.hasChatScreen(player));

        screenManager.handleChatInput(player, "one");
        assertTrue(screenManager.hasActiveScreen(player));
        assertTrue(screenManager.hasChatScreen(player));

        screenManager.handleChatInput(player, "two");
        assertTrue(screenManager.hasActiveScreen(player));
        assertTrue(screenManager.hasChatScreen(player));

        screenManager.handleChatInput(player, "three");
        assertFalse(screenManager.hasActiveScreen(player));
    }

    @Test
    void singlePromptReturnsAssembledCommand() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <test> please");

        assertTrue(screenManager.hasActiveScreen(player));
        screenManager.handleChatInput(player, "myValue");
        assertFalse(screenManager.hasActiveScreen(player));

        var sessionOpt = engine.getSession(player);
        assertTrue(sessionOpt.isEmpty());
    }

    @Test
    void cancelDuringMultiPromptRemovesSession() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <First> <Second> <Third>");

        assertTrue(screenManager.hasActiveScreen(player));
        screenManager.handleChatInput(player, "first");
        assertTrue(screenManager.hasActiveScreen(player));

        screenManager.cancelAll(player);
        assertFalse(screenManager.hasActiveScreen(player));
        assertFalse(engine.hasActiveSession(player));
    }

    @Test
    void cancelKeywordDuringMultiPromptCancelsSession() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <First> <Second> <Third>");

        assertTrue(screenManager.hasActiveScreen(player));
        screenManager.handleChatInput(player, "first");
        assertTrue(screenManager.hasActiveScreen(player));

        screenManager.handleChatInput(player, "cancel");
        assertFalse(screenManager.hasActiveScreen(player));
        assertFalse(engine.hasActiveSession(player));
    }
}
