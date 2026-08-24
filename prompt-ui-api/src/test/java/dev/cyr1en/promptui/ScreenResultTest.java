package dev.cyr1en.promptui;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.CancelReason;
import org.junit.jupiter.api.Test;

class ScreenResultTest {

    @Test
    void answerResultConstructedProperly() {
        var result = ScreenResult.answer("hello world");
        assertEquals("hello world", result.answer());
        assertFalse(result.cancelled());
        assertEquals(ScreenResult.Outcome.SUCCESS, result.outcome());
        assertNull(result.cancelReason());
    }

    @Test
    void defaultCancelUsesGuiExit() {
        var result = ScreenResult.cancel();
        assertEquals("", result.answer());
        assertTrue(result.cancelled());
        assertEquals(ScreenResult.Outcome.CANCELLED, result.outcome());
        assertEquals(CancelReason.GUI_EXIT, result.cancelReason());
    }

    @Test
    void typedCancelReasons() {
        assertEquals(CancelReason.MANUAL, ScreenResult.manualCancel().cancelReason());
        assertEquals(CancelReason.TIMEOUT, ScreenResult.timeout().cancelReason());
        assertEquals(CancelReason.GUI_EXIT, ScreenResult.guiExit().cancelReason());
        assertEquals(CancelReason.BLANK_INPUT, ScreenResult.blankInput().cancelReason());
        assertEquals(CancelReason.ERROR, ScreenResult.error().cancelReason());
    }

    @Test
    void customCancelWithReason() {
        var result = ScreenResult.cancel(CancelReason.TIMEOUT);
        assertTrue(result.cancelled());
        assertEquals(CancelReason.TIMEOUT, result.cancelReason());
    }

    @Test
    void legacyBooleanConstructor() {
        var success = new ScreenResult("answer", false);
        assertEquals("answer", success.answer());
        assertFalse(success.cancelled());
        assertEquals(ScreenResult.Outcome.SUCCESS, success.outcome());
        assertNull(success.cancelReason());

        var cancelled = new ScreenResult("", true);
        assertEquals("", cancelled.answer());
        assertTrue(cancelled.cancelled());
        assertEquals(ScreenResult.Outcome.CANCELLED, cancelled.outcome());
        assertEquals(CancelReason.GUI_EXIT, cancelled.cancelReason());
    }
}
