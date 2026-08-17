package dev.cyr1en.promptpaper.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptui.ScreenProvider;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.SignInputScreen;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
    void asynchronousProviderFailureFallsBackToChat() {
        var player = createPlayer();
        var mockProvider = mock(ScreenProvider.class);
        var mockSign = mock(SignInputScreen.class);
        var failureCallback = new AtomicReference<Consumer<Throwable>>();
        doAnswer(invocation -> {
            failureCallback.set(invocation.getArgument(0));
            return null;
        }).when(mockSign).onOpenFailure(any());
        when(mockProvider.createSign(any(CommandPrompter.class), any(), any()))
                .thenReturn(mockSign);

        var screen = new SignPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.SignPrompt("sign", "inline-test", "Enter:", java.util.List.of(), true), List.of(mockProvider));
        screen.open();

        assertNotNull(failureCallback.get());
        failureCallback.get().accept(new IllegalStateException("async open failed"));
        assertTrue(screen.isOpen());
        verify(mockSign).close();
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
    void handleResultWithoutSanitizePreservesColorCodes() {
        var player = createPlayer();
        var screen = new SignPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.SignPrompt("sign", "inline-test", "Enter value", java.util.List.of(), false), emptyProviders);
        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);
        screen.open();

        screen.handleResult(ScreenResult.answer("§cHello"));
        assertNotNull(resultRef.get());
        assertEquals("§cHello", resultRef.get().answer());
        assertFalse(resultRef.get().cancelled());
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

    @Test
    void arrangeLinesHandlesAllLocations() {
        String[] parts = new String[]{"Prompt 1", "Prompt 2", "Prompt 3"};

        // top location
        assertArrayEquals(new String[]{"", "Prompt 1", "", ""}, SignPromptScreen.arrangeLines(parts, 1, "top"));
        assertArrayEquals(new String[]{"", "Prompt 1", "Prompt 2", ""}, SignPromptScreen.arrangeLines(parts, 2, "top"));
        assertArrayEquals(new String[]{"", "Prompt 1", "Prompt 2", "Prompt 3"}, SignPromptScreen.arrangeLines(parts, 3, "top"));

        // top-aggregate location
        assertArrayEquals(new String[]{"", "", "", "Prompt 1"}, SignPromptScreen.arrangeLines(parts, 1, "top-aggregate"));
        assertArrayEquals(new String[]{"", "", "Prompt 1", "Prompt 2"}, SignPromptScreen.arrangeLines(parts, 2, "top-aggregate"));
        assertArrayEquals(new String[]{"", "Prompt 1", "Prompt 2", "Prompt 3"}, SignPromptScreen.arrangeLines(parts, 3, "top-aggregate"));

        // bottom location
        assertArrayEquals(new String[]{"Prompt 1", "", "", ""}, SignPromptScreen.arrangeLines(parts, 1, "bottom"));
        assertArrayEquals(new String[]{"Prompt 1", "Prompt 2", "", ""}, SignPromptScreen.arrangeLines(parts, 2, "bottom"));
        assertArrayEquals(new String[]{"Prompt 1", "Prompt 2", "Prompt 3", ""}, SignPromptScreen.arrangeLines(parts, 3, "bottom"));

        // bottom-aggregate location
        assertArrayEquals(new String[]{"Prompt 1", "", "", ""}, SignPromptScreen.arrangeLines(parts, 1, "bottom-aggregate"));
        assertArrayEquals(new String[]{"Prompt 1", "Prompt 2", "", ""}, SignPromptScreen.arrangeLines(parts, 2, "bottom-aggregate"));
        assertArrayEquals(new String[]{"Prompt 1", "Prompt 2", "Prompt 3", ""}, SignPromptScreen.arrangeLines(parts, 3, "bottom-aggregate"));

        // fallback default location
        assertArrayEquals(new String[]{"Prompt 1", "", "", ""}, SignPromptScreen.arrangeLines(parts, 1, null));
    }

    @Test
    void handleResultTopExtractsFirstLine() {
        when(promptConfig.inputFieldLocation()).thenReturn("top");
        var player = createPlayer();
        var screen = new SignPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.SignPrompt("sign", "inline-test", "Enter value", java.util.List.of(), true), emptyProviders);
        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);
        screen.open();

        screen.handleResult(ScreenResult.answer("myInput\nEnter value\n\n"));
        assertNotNull(resultRef.get());
        assertEquals("myInput", resultRef.get().answer());
    }

    @Test
    void handleResultBottomExtractsFourthLine() {
        when(promptConfig.inputFieldLocation()).thenReturn("bottom");
        var player = createPlayer();
        var screen = new SignPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.SignPrompt("sign", "inline-test", "Enter value{br}Confirm", java.util.List.of(), true), emptyProviders);
        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);
        screen.open();

        screen.handleResult(ScreenResult.answer("Enter value\nConfirm\n\nmyInput"));
        assertNotNull(resultRef.get());
        assertEquals("myInput", resultRef.get().answer());
    }

    @Test
    void handleResultTopAggregateExtractsNonPromptLines() {
        when(promptConfig.inputFieldLocation()).thenReturn("top-aggregate");
        var player = createPlayer();
        var screen = new SignPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.SignPrompt("sign", "inline-test", "Prompt 1{br}Prompt 2", java.util.List.of(), true), emptyProviders);
        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);
        screen.open();

        screen.handleResult(ScreenResult.answer("first\nsecond\nPrompt 1\nPrompt 2"));
        assertNotNull(resultRef.get());
        assertEquals("first second", resultRef.get().answer());
    }

    @Test
    void handleResultBottomAggregateExtractsNonPromptLines() {
        when(promptConfig.inputFieldLocation()).thenReturn("bottom-aggregate");
        var player = createPlayer();
        var screen = new SignPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.SignPrompt("sign", "inline-test", "Prompt 1", java.util.List.of(), true), emptyProviders);
        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);
        screen.open();

        screen.handleResult(ScreenResult.answer("Prompt 1\nfirst\nsecond\nthird"));
        assertNotNull(resultRef.get());
        assertEquals("first second third", resultRef.get().answer());
    }

    @Test
    void handleResultPresetWithDefaultLinesFiltersPromptLines() {
        var player = createPlayer();
        var preset = new dev.cyr1en.promptpaper.preset.SignPrompt(
                "sign", "preset-sign", "Enter value", List.of("Line 1", "Line 2", "", ""), true);
        var screen = new SignPromptScreen(plugin, player, preset, emptyProviders);
        var resultRef = new AtomicReference<ScreenResult>();
        screen.onResult(resultRef::set);
        screen.open();

        screen.handleResult(ScreenResult.answer("Line 1\nLine 2\ncustom1\ncustom2"));
        assertNotNull(resultRef.get());
        assertEquals("custom1 custom2", resultRef.get().answer());
    }
}
