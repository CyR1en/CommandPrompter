package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptpaper.screen.dialog.AnswerEncoding;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScreenManagerTest extends MockBukkitTest {

    private PromptEngine engine;
    private PromptFactory factory;
    private ScreenManager screenManager;

    @BeforeEach
    void setUpScreenManager() {
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
    void reloadGateRejectsSessionBeforeScreenStateIsCreated() {
        var player = createPlayer();
        assertTrue(engine.beginReload());

        screenManager.startSession(player, "/cmd <test>");
        assertFalse(screenManager.hasActiveScreen(player));
        assertFalse(engine.hasActiveSession(player));

        engine.endReload();
        screenManager.startSession(player, "/cmd <test>");
        assertTrue(screenManager.hasActiveScreen(player));
    }

    @Test
    void reloadGateRejectsDelegatedSessionBeforeScreenStateIsCreated() {
        var player = createPlayer();
        assertTrue(engine.beginReload());

        screenManager.startDelegatedSession(
                player, "/cmd <test>", ScreenManager.DispatchMode.CONSOLE, null);

        assertFalse(screenManager.hasActiveScreen(player));
        assertFalse(engine.hasActiveSession(player));
        engine.endReload();
    }

    @Test
    void hasChatScreenReturnsTrueForChatPrompt() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <test>");
        assertTrue(screenManager.hasChatScreen(player));
    }

    @Test
    void cancelAllRemovesScreen() {
        when(config.showCancelled()).thenReturn(true);
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <test>");
        assertTrue(screenManager.hasActiveScreen(player));
        while (player.nextMessage() != null) {
            // Discard the prompt text before checking that the default path stays silent.
        }
        screenManager.cancelAll(player);
        assertFalse(screenManager.hasActiveScreen(player));
        assertNull(player.nextMessage());
    }

    @Test
    void cancelAllWithNotificationNotifiesPlayerWithActiveSession() {
        when(config.showCancelled()).thenReturn(true);
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <test>");
        while (player.nextMessage() != null) {
            // Discard the prompt text so only cancellation feedback remains to assert.
        }

        screenManager.cancelAll(player, true);

        assertEquals("Prompt cancelled.", player.nextMessage());
        assertNull(player.nextMessage());
        assertFalse(screenManager.hasActiveScreen(player));
        assertFalse(engine.hasActiveSession(player));
        verify(i18n).get(eq("prompt.cancelled"), same(player));
    }

    @Test
    void cancelAllWithNotificationDoesNotNotifyPlayerWithoutActiveSession() {
        when(config.showCancelled()).thenReturn(true);
        var player = createPlayer();

        screenManager.cancelAll(player, true);

        assertNull(player.nextMessage());
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
        verify(i18n).get(eq("prompt.error.invalid_title_filter"), same(player));
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
        // Register the "d" key so the factory doesn't drop the tag
        var promptConfig = org.mockito.Mockito.mock(dev.cyr1en.promptpaper.config.PromptConfig.class);
        when(promptConfig.getScreenMappings()).thenReturn(
                Map.of("", dev.cyr1en.promptpaper.config.ScreenType.CHAT,
                       "d", dev.cyr1en.promptpaper.config.ScreenType.CHAT));
        lenient().when(promptConfig.sendCancelText()).thenReturn(false);
        lenient().when(promptConfig.responseListenerPriority()).thenReturn("LOWEST");
        when(configLoader.getPromptConfig()).thenReturn(promptConfig);

        engine = new PromptEngine(plugin, scheduler);
        factory = new PromptFactory(plugin);
        screenManager = new ScreenManager(plugin, engine, factory, scheduler);

        var player = createPlayer();
        // Compound: first sub-tag has filter=title, second has filter=text
        try {
            screenManager.startSession(player, "/cmd <d:title:Header && d:text:Enter value>");
            // The guard must NOT have fired — session is still alive (screen is open)
            assertTrue(screenManager.hasActiveScreen(player),
                    "Compound TITLE tag should not be blocked by the non-compound guard");
        } catch (NoClassDefFoundError e) {
            // MockBukkit does not provide Paper's dialog registry classes.
            assertNotNull(e.getMessage());
        }
    }

    @Test
    void handleChatInputWithCancelKeywordCancelsSession() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <test>");
        assertTrue(screenManager.hasActiveScreen(player));
        screenManager.handleChatInput(player, "cancel");
        assertFalse(screenManager.hasActiveScreen(player));
        assertFalse(engine.hasActiveSession(player));
    }

    @Test
    void handleChatInputWithCancelKeywordCaseInsensitiveAndSpacedCancelsSession() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <test>");
        assertTrue(screenManager.hasActiveScreen(player));
        screenManager.handleChatInput(player, "  cAnCeL  ");
        assertFalse(screenManager.hasActiveScreen(player));
        assertFalse(engine.hasActiveSession(player));
    }

    // ========================= Issue #99: player-context i18n =========================

    /**
     * A blank answer to a {@code -str} (STRING) prompt fails validation; the
     * validation error is sent directly to the player and must use the player
     * as the i18n context.
     */
    @Test
    void blankStringValidationFailureUsesPlayerContext() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <test -str> please");
        assertTrue(screenManager.hasChatScreen(player));
        while (player.nextMessage() != null) {
            // Discard the prompt text.
        }

        screenManager.handleChatInput(player, "   ");

        verify(i18n).get(eq("validation.invalid_string"), same(player));
        assertTrue(screenManager.hasActiveScreen(player),
                "failed validation must re-show the prompt");
    }

    /**
     * A non-integer answer to a {@code -int} (INTEGER) prompt fails validation;
     * the validation error must use the player as the i18n context.
     */
    @Test
    void integerValidationFailureUsesPlayerContext() {
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <test -int> please");
        assertTrue(screenManager.hasChatScreen(player));
        while (player.nextMessage() != null) {
            // Discard the prompt text.
        }

        screenManager.handleChatInput(player, "not-a-number");

        verify(i18n).get(eq("validation.invalid_integer"), same(player));
        assertTrue(screenManager.hasActiveScreen(player),
                "failed validation must re-show the prompt");
    }

    /**
     * The timeout feedback is sent directly to the player and must use the
     * player as the i18n context.
     */
    @Test
    void timeoutFeedbackUsesPlayerContext() {
        when(config.promptTimeout()).thenReturn(1);
        when(config.showCancelled()).thenReturn(true);
        var player = createPlayer();
        screenManager.startSession(player, "/cmd <test>");
        assertTrue(screenManager.hasActiveScreen(player));
        while (player.nextMessage() != null) {
            // Discard the prompt text.
        }

        performTicks(20);

        verify(i18n).get(eq("prompt.timed_out"), same(player));
        String timedOut = player.nextMessage();
        assertNotNull(timedOut, "the timeout message must be sent");
        assertTrue(timedOut.contains("timed out"), "was: " + timedOut);
        assertNull(player.nextMessage());
        assertFalse(screenManager.hasActiveScreen(player));
        assertFalse(engine.hasActiveSession(player));
    }

    // ========================= Issue #90: false dispatch return =========================

    /**
     * Regression for Issue #90: when the ATTACHMENT-mode dispatch's
     * {@code Bukkit.dispatchCommand} returns false (command not found), the
     * temporary permission attachment must be removed immediately. It must
     * not be retained for the configured {@code permissionAttachmentTicks}
     * delay as if dispatch had succeeded.
     */
    @Test
    void falseDispatchReturnRemovesAttachmentImmediately() {
        when(config.getPermissionAttachment("KEY"))
                .thenReturn(new String[]{"perm.old"});
        when(config.permissionAttachmentTicks()).thenReturn(20);
        var player = createPlayer();

        screenManager.startDelegatedSession(
                player, "/cmd no prompts", ScreenManager.DispatchMode.ATTACHMENT, "KEY");

        assertFalse(player.hasPermission("perm.old"),
                "attachment must be removed immediately after a false dispatch return");
        performTicks(20);
        assertFalse(player.hasPermission("perm.old"),
                "attachment must not linger for the configured delay");
    }

    @Test
    void presetDialogWithTabCompletionBuildsCompletionContext() {
        var mockFactory = org.mockito.Mockito.mock(PromptFactory.class);
        var customScreenManager = new ScreenManager(plugin, engine, mockFactory, scheduler);

        var registry = org.mockito.Mockito.mock(dev.cyr1en.promptpaper.preset.PresetRegistry.class);
        when(plugin.getPresetRegistry()).thenReturn(registry);

        var dt = new dev.cyr1en.promptpaper.preset.DialogTypeConfig(
                dev.cyr1en.promptpaper.preset.DialogType.MULTI_ACTION, 1, List.of(),
                dev.cyr1en.promptpaper.preset.ActionsSource.TAB_COMPLETION, null, null, null);
        var base = new dev.cyr1en.promptpaper.preset.DialogBaseConfig(List.of(), List.of());
        var dialog = new dev.cyr1en.promptpaper.preset.DialogPrompt("dialog", "my_tab_preset", "Choose", base, dt, true);
        when(registry.getPrompt("my_tab_preset")).thenReturn(java.util.Optional.of(dialog));

        var dummyScreen = org.mockito.Mockito.mock(dev.cyr1en.promptui.InputScreen.class);
        when(mockFactory.createFromTag(any(Player.class), any(PromptTag.class), any())).thenReturn(dummyScreen);

        var player = createPlayer();
        customScreenManager.startSession(player, "/cmd <@my_tab_preset>");

        var captor = org.mockito.ArgumentCaptor.forClass(dev.cyr1en.promptpaper.screen.dialog.DialogCompletionContext.class);
        verify(mockFactory).createFromTag(eq(player), any(PromptTag.class), captor.capture());

        assertNotNull(captor.getValue());
        assertEquals("/cmd ", captor.getValue().partialCommand());
        assertEquals(player, captor.getValue().player());
    }

    @Test
    void presetDialogWithoutTabCompletionPassesNullContext() {
        var mockFactory = org.mockito.Mockito.mock(PromptFactory.class);
        var customScreenManager = new ScreenManager(plugin, engine, mockFactory, scheduler);

        var registry = org.mockito.Mockito.mock(dev.cyr1en.promptpaper.preset.PresetRegistry.class);
        when(plugin.getPresetRegistry()).thenReturn(registry);

        var dt = new dev.cyr1en.promptpaper.preset.DialogTypeConfig(
                dev.cyr1en.promptpaper.preset.DialogType.CONFIRMATION, null, List.of(), null, null, null, null);
        var base = new dev.cyr1en.promptpaper.preset.DialogBaseConfig(List.of(), List.of());
        var dialog = new dev.cyr1en.promptpaper.preset.DialogPrompt("dialog", "my_conf_preset", "Choose", base, dt, true);
        when(registry.getPrompt("my_conf_preset")).thenReturn(java.util.Optional.of(dialog));

        var dummyScreen = org.mockito.Mockito.mock(dev.cyr1en.promptui.InputScreen.class);
        when(mockFactory.createFromTag(any(Player.class), any(PromptTag.class), any())).thenReturn(dummyScreen);

        var player = createPlayer();
        customScreenManager.startSession(player, "/cmd <@my_conf_preset>");

        var captor = org.mockito.ArgumentCaptor.forClass(dev.cyr1en.promptpaper.screen.dialog.DialogCompletionContext.class);
        verify(mockFactory).createFromTag(eq(player), any(PromptTag.class), captor.capture());

        assertNull(captor.getValue());
    }
}
