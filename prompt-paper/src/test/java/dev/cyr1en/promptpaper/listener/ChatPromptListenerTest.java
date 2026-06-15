package dev.cyr1en.promptpaper.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatPromptListenerTest extends MockBukkitTest {

    private ScreenManager screenManager;
    private ChatPromptListener listener;

    @BeforeEach
    void setUpListener() {
        screenManager = org.mockito.Mockito.mock(ScreenManager.class);
        listener = new ChatPromptListener(plugin, screenManager);
    }

    @Test
    void priorityDefaultsToLowest() {
        when(promptConfig.responseListenerPriority()).thenReturn("LOWEST");
        assertEquals(EventPriority.LOWEST, listener.resolvePriority());
    }

    @Test
    void priorityParsesHigh() {
        when(promptConfig.responseListenerPriority()).thenReturn("HIGH");
        assertEquals(EventPriority.HIGH, listener.resolvePriority());
    }

    @Test
    void invalidPriorityFallsBackToLowest() {
        when(promptConfig.responseListenerPriority()).thenReturn("INVALID");
        assertEquals(EventPriority.LOWEST, listener.resolvePriority());
    }

    @Test
    void nullPriorityFallsBackToLowest() {
        when(promptConfig.responseListenerPriority()).thenReturn(null);
        assertEquals(EventPriority.LOWEST, listener.resolvePriority());
    }

    @Test
    void chatEventWithNoChatScreenIsNotCancelled() {
        when(screenManager.hasChatScreen(any())).thenReturn(false);
        var player = createPlayer();
        var event = new AsyncPlayerChatEvent(false, player, "hello", null);
        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
    }
}
