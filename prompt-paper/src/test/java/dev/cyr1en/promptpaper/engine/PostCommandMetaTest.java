package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptpaper.MockBukkitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostCommandMetaTest extends MockBukkitTest {

    private PromptEngine engine;

    @BeforeEach
    void setUpEngine() {
        engine = new PromptEngine(plugin, scheduler);
    }

    @Test
    void onCompletePcmDispatchesAfterSessionCompletes() {
        var player = createPlayer();
        engine.intercept(player, "/cmd <test> <!log done>");

        var result = engine.submit(player, "value");
        assertTrue(result.isPresent());
        assertFalse(result.get().onCompleteCmds().isEmpty());
    }

    @Test
    void onCancelPcmDispatchesOnCancel() {
        var player = createPlayer();
        engine.intercept(player, "/cmd <test> <!!log cancelled>");

        assertTrue(engine.hasActiveSession(player));
        engine.cancel(player, CancelReason.MANUAL);
        assertFalse(engine.hasActiveSession(player));
    }

    @Test
    void noPcmWhenNoPostCommandMeta() {
        var player = createPlayer();
        engine.intercept(player, "/cmd <test>");

        var result = engine.submit(player, "value");
        assertTrue(result.isPresent());
        assertTrue(result.get().onCompleteCmds().isEmpty());
        assertTrue(result.get().onCancelCmds().isEmpty());
    }

    @Test
    void sessionWithBothPcmTypesOnlyOnCompleteWhenCompleted() {
        var player = createPlayer();
        engine.intercept(player, "/cmd <test> <!complete> <!!cancel>");

        var result = engine.submit(player, "value");
        assertTrue(result.isPresent());
        assertEquals(1, result.get().onCompleteCmds().size());
        assertTrue(result.get().onCancelCmds().isEmpty());
        assertEquals("complete", result.get().onCompleteCmds().getFirst().command());
    }

    @Test
    void onCancelPcmOnlyPopulatedWhenCancelled() {
        var player = createPlayer();
        engine.intercept(player, "/cmd <test> <!complete> <!!cancel>");

        assertTrue(engine.hasActiveSession(player));
        engine.cancel(player, CancelReason.MANUAL);
        assertFalse(engine.hasActiveSession(player));
    }

    @Test
    void pcmWithPlayerTargetDispatchesAsPlayer() {
        var player = createPlayer();
        engine.intercept(player, "/cmd <test> <!/player value>");

        var result = engine.submit(player, "value");
        assertTrue(result.isPresent());
        var pcm = result.get().onCompleteCmds().getFirst();
        assertEquals("/player value", pcm.command());
    }

    @Test
    void pcmWithDelayReturnsCorrectTicks() {
        var player = createPlayer();
        engine.intercept(player, "/cmd <test> <!log done>");

        var result = engine.submit(player, "value");
        var pcm = result.get().onCompleteCmds().getFirst();
        assertEquals(0, pcm.delayTicks());
    }
}
