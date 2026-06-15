package dev.cyr1en.promptpaper;

import static org.junit.jupiter.api.Assertions.*;
import dev.cyr1en.promptpaper.testutil.MockScheduler;
import dev.cyr1en.promptpaper.util.CancellableTask;
import dev.cyr1en.promptpaper.util.FormatUtil;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class SmokeTest extends MockBukkitTest {

    @Test
    void mockBukkitServerIsRunning() {
        assertNotNull(server);
        assertNotNull(plugin);
    }

    @Test
    void playerCanBeCreated() {
        var player = createPlayer("TestPlayer");
        assertEquals("TestPlayer", player.getName());
        assertTrue(player.isOnline());
    }

    @Test
    void schedulerRunsSyncInline() {
        var ran = new AtomicBoolean(false);
        scheduler.runSync(() -> ran.set(true));
        assertTrue(ran.get());
    }

    @Test
    void schedulerRunLaterAdvancesWithTicks() {
        var ran = new AtomicBoolean(false);
        scheduler.runLater(() -> ran.set(true), 5);
        assertFalse(ran.get());
        performTicks(5);
        assertTrue(ran.get());
    }

    @Test
    void formatUtilWorksWithoutBukkit() {
        var result = FormatUtil.safeFormat("Hello %s", "World");
        assertEquals("Hello World", result);
    }

    @Test
    void schedulerRunLaterReturnsCancellable() {
        var ran = new AtomicBoolean(false);
        CancellableTask task = scheduler.runLater(() -> ran.set(true), 10);
        assertNotNull(task);
        task.cancel();
        performTicks(10);
        assertFalse(ran.get());
    }
}
