package dev.cyr1en.promptpaper.approval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApprovalDecisionTest {

  @Test
  @DisplayName("isApproved returns true only for APPROVED")
  void testIsApproved() {
    assertTrue(ApprovalDecision.APPROVED.isApproved());
    assertFalse(ApprovalDecision.DENIED.isApproved());
    assertFalse(ApprovalDecision.TIMED_OUT.isApproved());
    assertFalse(ApprovalDecision.TARGET_DISCONNECTED.isApproved());
    assertFalse(ApprovalDecision.INITIATOR_DISCONNECTED.isApproved());
  }

  @Test
  @DisplayName("All decisions are terminal")
  void testIsTerminal() {
    for (ApprovalDecision decision : ApprovalDecision.values()) {
      assertTrue(decision.isTerminal());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"confirm", "CONFIRM", "approve", "Approved", "yes", "accept"})
  @DisplayName("parse recognizes approval aliases")
  void testParseApproved(String raw) {
    Optional<ApprovalDecision> parsed = ApprovalDecision.parse(raw);
    assertTrue(parsed.isPresent());
    assertEquals(ApprovalDecision.APPROVED, parsed.get());
  }

  @ParameterizedTest
  @ValueSource(strings = {"decline", "DECLINE", "deny", "Denied", "no", "reject", "cancel"})
  @DisplayName("parse recognizes denial aliases")
  void testParseDenied(String raw) {
    Optional<ApprovalDecision> parsed = ApprovalDecision.parse(raw);
    assertTrue(parsed.isPresent());
    assertEquals(ApprovalDecision.DENIED, parsed.get());
  }

  @Test
  @DisplayName("parse recognizes disconnect and timeout decisions")
  void testParseOtherDecisions() {
    assertEquals(Optional.of(ApprovalDecision.TIMED_OUT), ApprovalDecision.parse("timed_out"));
    assertEquals(Optional.of(ApprovalDecision.TIMED_OUT), ApprovalDecision.parse("timeout"));
    assertEquals(
        Optional.of(ApprovalDecision.TARGET_DISCONNECTED),
        ApprovalDecision.parse("target_disconnected"));
    assertEquals(
        Optional.of(ApprovalDecision.INITIATOR_DISCONNECTED),
        ApprovalDecision.parse("initiator_disconnected"));
  }

  @Test
  @DisplayName("parse returns empty on unknown or null/blank inputs")
  void testParseInvalid() {
    assertTrue(ApprovalDecision.parse(null).isEmpty());
    assertTrue(ApprovalDecision.parse("").isEmpty());
    assertTrue(ApprovalDecision.parse("   ").isEmpty());
    assertTrue(ApprovalDecision.parse("unknown_command").isEmpty());
  }
}
