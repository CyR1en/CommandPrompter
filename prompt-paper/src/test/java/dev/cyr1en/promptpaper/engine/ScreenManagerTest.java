package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.screen.ScreenRouter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScreenManagerTest extends MockBukkitTest {

    private PromptEngine engine;
    private ScreenRouter router;
    private ScreenManager screenManager;

    @BeforeEach
    void setUpScreenManager() {
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
    void startSessionWithNoPromptsDoesNothing() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd no prompts");
        assertFalse(screenManager.hasActiveScreen(player));
    }

    @Test
    void startSessionWithPromptsCreatesScreen() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <test>");
        assertTrue(screenManager.hasActiveScreen(player));
    }

    @Test
    void hasChatScreenReturnsTrueForChatPrompt() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <test>");
        assertTrue(screenManager.hasChatScreen(player));
    }

    @Test
    void cancelAllRemovesScreen() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <test>");
        assertTrue(screenManager.hasActiveScreen(player));
        screenManager.cancelAll(player);
        assertFalse(screenManager.hasActiveScreen(player));
    }

    @Test
    void startDelegatedSessionWithoutPromptsDoesNotCreateScreen() {
        var player = createPlayer();
        screenManager.startDelegatedSession(player, "/cmd no prompts",
                ScreenManager.DispatchMode.CONSOLE, null);
        assertFalse(screenManager.hasActiveScreen(player));
    }

    @Test
    void startDelegatedSessionWithPromptsCreatesScreen() {
        var player = createPlayer();
        screenManager.startDelegatedSession(player, "/cmd <test>",
                ScreenManager.DispatchMode.CONSOLE, null);
        assertTrue(screenManager.hasActiveScreen(player));
    }

    @Test
    void handleChatInputProcessesAnswer() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <test>");
        assertTrue(screenManager.hasChatScreen(player));
        screenManager.handleChatInput(player, "myValue");
        assertFalse(screenManager.hasActiveScreen(player));
    }

    @Test
    void handleChatInputWithoutChatScreenIsNoop() {
        var player = createPlayer();
        screenManager.handleChatInput(player, "value");
        assertFalse(screenManager.hasActiveScreen(player));
    }

    // ========================= TITLE-filter guard =========================

    /**
     * A non-compound tag with filter=title (e.g. {@code <d:title:Prompt>}) is
     * invalid: TITLE is a block-level decoration for compound dialogs, not a
     * standalone input type. The session must be cancelled without opening any
     * screen.
     */
    @Test
    void nonCompoundTitleFilterAbortsSessionAndCreatesNoScreen() {
        var player = createPlayer();
        // <d:title:Prompt> parses to key="d", filter="title", isCompound()=false
        screenManager.startSession(player, "/cmd <d:title:Prompt>");
        assertFalse(screenManager.hasActiveScreen(player),
                "No screen should be opened for a non-compound TITLE tag");
        assertFalse(engine.hasActiveSession(player),
                "Session must be cancelled when TITLE guard fires");
    }

    /**
     * A compound tag that contains a title sub-row together with other input
     * kinds is a legitimate dialog configuration. The TITLE guard must not fire
     * for compound tags, so the session proceeds normally (here it would try to
     * open a Dialog screen; since MockBukkit lacks the Paper Dialog API it falls
     * back to chat, which is still an active screen — enough to prove the guard
     * was not triggered).
     */
    @Test
    void compoundTagWithTitleFilterIsAllowedThrough() {
        // Register the "d" key so the router doesn't drop the tag
        var promptConfig = org.mockito.Mockito.mock(dev.cyr1en.promptpaper.config.PromptConfig.class);
        when(promptConfig.getScreenMappings()).thenReturn(
                Map.of("", dev.cyr1en.promptpaper.config.ScreenType.CHAT,
                       "d", dev.cyr1en.promptpaper.config.ScreenType.CHAT));
        lenient().when(promptConfig.sendCancelText()).thenReturn(false);
        lenient().when(promptConfig.responseListenerPriority()).thenReturn("LOWEST");
        when(configLoader.getPromptConfig()).thenReturn(promptConfig);

        engine = new PromptEngine(plugin, scheduler);
        router = new ScreenRouter(plugin);
        screenManager = new ScreenManager(plugin, engine, router, scheduler);

        var player = createPlayer();
        // Compound: first sub-tag has filter=title, second has filter=text
        screenManager.startSession(player, "/cmd <d:title:Header && d:text:Enter value>");
        // The guard must NOT have fired — session is still alive (screen is open)
        assertTrue(screenManager.hasActiveScreen(player),
                "Compound TITLE tag should not be blocked by the non-compound guard");
    }
}
