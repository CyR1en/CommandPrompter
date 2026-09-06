package dev.cyr1en.promptpaper.screen.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.CancelReason;
import org.junit.jupiter.api.Test;

class ConfirmationOutcomeTest {

  @Test
  void confirmedOutcome() {
    var outcome = ConfirmationOutcome.confirmed();
    assertTrue(outcome.isConfirmed());
    assertFalse(outcome.isDeclined());
    assertFalse(outcome.isCancelled());
    assertInstanceOf(ConfirmationOutcome.Confirmed.class, outcome);
  }

  @Test
  void declinedOutcome() {
    var outcome = ConfirmationOutcome.declined();
    assertFalse(outcome.isConfirmed());
    assertTrue(outcome.isDeclined());
    assertFalse(outcome.isCancelled());
    assertInstanceOf(ConfirmationOutcome.Declined.class, outcome);
  }

  @Test
  void cancelledOutcome() {
    var outcome = ConfirmationOutcome.cancelled(CancelReason.TIMEOUT);
    assertFalse(outcome.isConfirmed());
    assertFalse(outcome.isDeclined());
    assertTrue(outcome.isCancelled());
    assertInstanceOf(ConfirmationOutcome.Cancelled.class, outcome);

    var cancelled = (ConfirmationOutcome.Cancelled) outcome;
    assertEquals(CancelReason.TIMEOUT, cancelled.reason());
  }

  @Test
  void cancelledNullReasonThrows() {
    assertThrows(NullPointerException.class, () -> ConfirmationOutcome.cancelled(null));
    assertThrows(NullPointerException.class, () -> new ConfirmationOutcome.Cancelled(null));
  }

  @Test
  void patternMatchingExhaustive() {
    ConfirmationOutcome outcome = ConfirmationOutcome.confirmed();
    String label =
        switch (outcome) {
          case ConfirmationOutcome.Confirmed c -> "CONFIRMED";
          case ConfirmationOutcome.Declined d -> "DECLINED";
          case ConfirmationOutcome.Cancelled c -> "CANCELLED:" + c.reason();
        };
    assertEquals("CONFIRMED", label);
  }
}
