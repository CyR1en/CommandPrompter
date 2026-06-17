package dev.cyr1en.promptpaper.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptui.AnvilInputScreen;
import dev.cyr1en.promptui.ScreenProvider;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnvilPromptScreenTest extends MockBukkitTest {

    private List<ScreenProvider> emptyProviders;

    @BeforeEach
    void setUpAnvil() {
        emptyProviders = List.of();
        lenient().when(promptConfig.sendCancelText()).thenReturn(false);
    }

    @Test
    void constructorStoresValues() {
        var player = createPlayer();
        var screen = new AnvilPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.AnvilPrompt("anvil", "inline-test", "Anvil", "Enter:", new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0), new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0), true), emptyProviders);
        assertNotNull(screen);
        assertFalse(screen.isOpen());
    }

    @Test
    void openWithEmptyProvidersFallsBackToChat() {
        var player = createPlayer();
        var screen = new AnvilPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.AnvilPrompt("anvil", "inline-test", "Anvil", "Enter:", new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0), new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0), true), emptyProviders);
        screen.open();
        assertTrue(screen.isOpen());
    }

    @Test
    void openWithValidProviderDelegates() {
        var player = createPlayer();
        var mockProvider = mock(ScreenProvider.class);
        var mockAnvil = mock(AnvilInputScreen.class);
        when(mockProvider.createAnvil(any(CommandPrompter.class), any(), anyString()))
                .thenReturn(mockAnvil);

        var screen = new AnvilPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.AnvilPrompt("anvil", "inline-test", "Anvil", "Enter:", new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0), new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0), true), List.of(mockProvider));
        screen.open();

        assertTrue(screen.isOpen());
        verify(mockAnvil).open();
    }

    @Test
    void handleResultWithAnswerFiresCallback() {
        var player = createPlayer();
        var screen = new AnvilPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.AnvilPrompt("anvil", "inline-test", "Anvil", "Enter:", new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0), new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0), true), emptyProviders);
        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);
        screen.open();

        screen.handleResult(ScreenResult.answer("  myAnswer  "));
        assertNotNull(resultRef.get());
        assertEquals("myAnswer", resultRef.get().answer());
        assertFalse(resultRef.get().cancelled());
    }

    @Test
    void handleResultWithCancelFiresCallback() {
        var player = createPlayer();
        var screen = new AnvilPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.AnvilPrompt("anvil", "inline-test", "Anvil", "Enter:", new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0), new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0), true), emptyProviders);
        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);
        screen.open();

        screen.handleResult(ScreenResult.cancel());
        assertNotNull(resultRef.get());
        assertTrue(resultRef.get().cancelled());
    }

    @Test
    void handleResultWithCancelKeywordReturnsCancel() {
        var player = createPlayer();
        when(config.cancelKeyword()).thenReturn("cancel");
        var screen = new AnvilPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.AnvilPrompt("anvil", "inline-test", "Anvil", "Enter:", new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0), new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0), true), emptyProviders);
        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);
        screen.open();

        screen.handleResult(ScreenResult.answer("  Cancel  "));
        assertNotNull(resultRef.get());
        assertTrue(resultRef.get().cancelled());
    }

    @Test
    void handleResultWithoutOpenIsNoop() {
        var player = createPlayer();
        var screen = new AnvilPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.AnvilPrompt("anvil", "inline-test", "Anvil", "Enter:", new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0), new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0), true), emptyProviders);
        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);

        screen.handleResult(ScreenResult.answer("value"));
        assertNull(resultRef.get());
    }
}
