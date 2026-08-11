package dev.cyr1en.promptpaper.screen.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cyr1en.promptui.ScreenResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DialogLifecycleTest {

    @Test
    void terminalResultClosesDialogBeforeNotifyingSessionManager() {
        var lifecycle = new DialogLifecycle();
        var events = new ArrayList<String>();
        var result = ScreenResult.answer("answer");
        lifecycle.onResult(received -> events.add("result:" + received.answer()));
        lifecycle.opened();

        assertTrue(lifecycle.finish(result, () -> events.add("close")));

        assertEquals(List.of("close", "result:answer"), events);
        assertFalse(lifecycle.isOpen());

        assertFalse(lifecycle.finish(result, () -> events.add("duplicate-close")));
        assertEquals(List.of("close", "result:answer"), events);
    }

    @Test
    void externalCloseDoesNotDeliverAResult() {
        var lifecycle = new DialogLifecycle();
        var events = new ArrayList<String>();
        lifecycle.onResult(result -> events.add("result"));
        lifecycle.opened();

        assertTrue(lifecycle.close(() -> events.add("close")));

        assertEquals(List.of("close"), events);
        assertFalse(lifecycle.isOpen());
    }
}
