package dev.cyr1en.promptpaper.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatPromptScreenTest extends MockBukkitTest {

    private ChatPromptScreen screen;
    private AtomicReference<ScreenResult> resultRef;

    @BeforeEach
    void setUpScreen() {
        var player = createPlayer("TestPlayer");
        lenient().when(promptConfig.sendCancelText()).thenReturn(false);
        screen = new ChatPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.ChatPrompt("chat", "inline-test", "Enter value:", new dev.cyr1en.promptpaper.preset.CancelBehavior(false, "", false, ""), true));
        resultRef = new AtomicReference<>();
        screen.onResult(resultRef::set);
    }

    @Test
    void isOpenReturnsFalseInitially() {
        assertFalse(screen.isOpen());
    }

    @Test
    void openSetsOpenToTrue() {
        screen.open();
        assertTrue(screen.isOpen());
    }

    @Test
    void closeSetsOpenToFalse() {
        screen.open();
        screen.close();
        assertFalse(screen.isOpen());
    }

    @Test
    void handleInputFiresCallbackWithAnswer() {
        screen.open();
        screen.handleInput("myAnswer");
        assertNotNull(resultRef.get());
        assertEquals("myAnswer", resultRef.get().answer());
        assertFalse(resultRef.get().cancelled());
    }

    @Test
    void handleInputWithoutOpenDoesNotFireCallback() {
        screen.handleInput("myAnswer");
        assertNull(resultRef.get());
    }

    @Test
    void handleInputClosesScreenBeforeCallback() {
        screen.open();
        screen.handleInput("test");
        assertFalse(screen.isOpen());
    }

    @Test
    void onResultReplacesPreviousCallback() {
        var secondRef = new AtomicReference<ScreenResult>();
        screen.onResult(secondRef::set);
        screen.open();
        screen.handleInput("test");
        assertNull(resultRef.get());
        assertNotNull(secondRef.get());
    }
}
