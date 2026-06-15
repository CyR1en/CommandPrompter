package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.screen.ChatPromptScreen;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.screen.ScreenRouter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiPromptIntegrationTest extends MockBukkitTest {

    private PromptEngine engine;
    private ScreenRouter router;
    private ScreenManager screenManager;

    @BeforeEach
    void setUpIntegration() {
        var promptConfig = org.mockito.Mockito.mock(dev.cyr1en.promptpaper.config.PromptConfig.class);
        when(promptConfig.getScreenMappings()).thenReturn(Map.of("", ScreenType.CHAT));
        lenient().when(promptConfig.sendCancelText()).thenReturn(false);
        lenient().when(promptConfig.responseListenerPriority()).thenReturn("LOWEST");
        when(configLoader.getPromptConfig()).thenReturn(promptConfig);

        engine = new PromptEngine(plugin, scheduler);
        router = new ScreenRouter(plugin);
        screenManager = new ScreenManager(plugin, engine, router, scheduler);
    }

    @Test
    void threePromptCommandCyclesThroughAllPrompts() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <a:First> <b:Second> <c:Third>");

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
        screenManager.startSession(player, "/cmd <a> <b> <c>");

        assertTrue(screenManager.hasActiveScreen(player));
        screenManager.handleChatInput(player, "first");
        assertTrue(screenManager.hasActiveScreen(player));

        screenManager.cancelAll(player);
        assertFalse(screenManager.hasActiveScreen(player));
        assertFalse(engine.hasActiveSession(player));
    }

    @Test
    void promptWithNoPromptsDoesNotCreateScreen() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd no prompts");
        assertFalse(screenManager.hasActiveScreen(player));
    }

    @Test
    void handleChatInputWithoutChatScreenIsNoop() {
        var player = createPlayer();
        screenManager.handleChatInput(player, "value");
        assertFalse(screenManager.hasActiveScreen(player));
    }
}
