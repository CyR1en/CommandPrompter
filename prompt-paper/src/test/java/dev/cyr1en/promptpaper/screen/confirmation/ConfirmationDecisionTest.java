package dev.cyr1en.promptpaper.screen.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConfirmationDecisionTest {

    @Test
    void parseValidConfirm() {
        assertEquals(ConfirmationDecision.CONFIRM, ConfirmationDecision.parse("confirm").orElse(null));
        assertEquals(ConfirmationDecision.CONFIRM, ConfirmationDecision.parse("CONFIRM").orElse(null));
        assertEquals(ConfirmationDecision.CONFIRM, ConfirmationDecision.parse("  Confirm  ").orElse(null));
    }

    @Test
    void parseValidDecline() {
        assertEquals(ConfirmationDecision.DECLINE, ConfirmationDecision.parse("decline").orElse(null));
        assertEquals(ConfirmationDecision.DECLINE, ConfirmationDecision.parse("DECLINE").orElse(null));
        assertEquals(ConfirmationDecision.DECLINE, ConfirmationDecision.parse("  Decline  ").orElse(null));
    }

    @Test
    void parseInvalidStrings() {
        assertTrue(ConfirmationDecision.parse(null).isEmpty());
        assertTrue(ConfirmationDecision.parse("").isEmpty());
        assertTrue(ConfirmationDecision.parse("   ").isEmpty());
        assertTrue(ConfirmationDecision.parse("yes").isEmpty());
        assertTrue(ConfirmationDecision.parse("no").isEmpty());
        assertTrue(ConfirmationDecision.parse("true").isEmpty());
        assertTrue(ConfirmationDecision.parse("cancel").isEmpty());
    }

    @Test
    void commandArgReturnsCorrectString() {
        assertEquals("confirm", ConfirmationDecision.CONFIRM.commandArg());
        assertEquals("decline", ConfirmationDecision.DECLINE.commandArg());
    }
}
