package dev.cyr1en.promptpaper.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptui.ScreenProvider;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.SignInputScreen;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SignPromptScreenTest extends MockBukkitTest {

    private List<ScreenProvider> emptyProviders;

    @BeforeEach
    void setUpSign() {
        emptyProviders = List.of();
        lenient().when(promptConfig.sendCancelText()).thenReturn(false);
        lenient().when(promptConfig.inputFieldLocation()).thenReturn("bottom");
        lenient().when(promptConfig.signMaterial()).thenReturn("OAK_SIGN");
    }

    @Test
    void constructorStoresValues() {
        var player = createPlayer();
        var screen = new SignPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.SignPrompt("sign", "inline-test", "Enter:", java.util.List.of(), true), emptyProviders);
        assertNotNull(screen);
        assertFalse(screen.isOpen());
    }

    @Test
    void openWithEmptyProvidersFallsBackToChat() {
        var player = createPlayer();
        var screen = new SignPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.SignPrompt("sign", "inline-test", "Enter:", java.util.List.of(), true), emptyProviders);
        screen.open();
        assertTrue(screen.isOpen());
    }

    @Test
    void openWithValidProviderDelegates() {
        var player = createPlayer();
        var mockProvider = mock(ScreenProvider.class);
        var mockSign = mock(SignInputScreen.class);
        when(mockProvider.createSign(any(CommandPrompter.class), any(), any()))
                .thenReturn(mockSign);

        var screen = new SignPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.SignPrompt("sign", "inline-test", "Enter:", java.util.List.of(), true), List.of(mockProvider));
        screen.open();

        assertTrue(screen.isOpen());
        verify(mockSign).open();
    }

    @Test
    void handleResultWithAnswerFiresCallback() {
        var player = createPlayer();
        var screen = new SignPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.SignPrompt("sign", "inline-test", "Enter value", java.util.List.of(), true), emptyProviders);
        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);
        screen.open();

        screen.handleResult(ScreenResult.answer("myAnswer"));
        assertNotNull(resultRef.get());
        assertEquals("myAnswer", resultRef.get().answer());
    }

    @Test
    void handleResultWithCancelFiresCallback() {
        var player = createPlayer();
        var screen = new SignPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.SignPrompt("sign", "inline-test", "Enter value", java.util.List.of(), true), emptyProviders);
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
        var screen = new SignPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.SignPrompt("sign", "inline-test", "Enter value", java.util.List.of(), true), emptyProviders);
        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);
        screen.open();

        screen.handleResult(ScreenResult.answer("cancel"));
        assertNotNull(resultRef.get());
        assertTrue(resultRef.get().cancelled());
    }

    @Test
    void handleResultWithoutOpenIsNoop() {
        var player = createPlayer();
        var screen = new SignPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.SignPrompt("sign", "inline-test", "Enter value", java.util.List.of(), true), emptyProviders);
        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);

        screen.handleResult(ScreenResult.answer("value"));
        assertNull(resultRef.get());
    }

    @Test
    void handleResultWithMultiArgExtractsValues() {
        var player = createPlayer();
        var screen = new SignPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.SignPrompt("sign", "inline-test", "name:{br}age:{br}email:", java.util.List.of(), true), emptyProviders);
        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);
        screen.open();

        var multiLine = "name:John\nage:30\nemail:john@test.com\n";
        screen.handleResult(ScreenResult.answer(multiLine));
        assertNotNull(resultRef.get());
        assertEquals("John 30 john@test.com", resultRef.get().answer());
    }

    @Test
    void handleResultNonMultiArgPreservesInput() {
        var player = createPlayer();
        var screen = new SignPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.SignPrompt("sign", "inline-test", "Enter value{br}Confirm", java.util.List.of(), true), emptyProviders);
        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);
        screen.open();

        screen.handleResult(ScreenResult.answer("hello"));
        assertNotNull(resultRef.get());
        assertEquals("hello", resultRef.get().answer());
    }
}
