package dev.cyr1en.promptpaper.approval;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record representing the terminal outcome of an approval capability evaluation.
 */
public record ApprovalOutcome(
    ApprovalCapability capability,
    ApprovalDecision decision,
    Instant timestamp) {

  public ApprovalOutcome {
    Objects.requireNonNull(capability, "capability must not be null");
    Objects.requireNonNull(decision, "decision must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
  }

  public static ApprovalOutcome of(
      ApprovalCapability capability, ApprovalDecision decision, Instant timestamp) {
    return new ApprovalOutcome(capability, decision, timestamp);
  }
}
