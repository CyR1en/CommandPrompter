package dev.cyr1en.promptpaper.approval;

import java.util.Locale;
import java.util.Optional;

/** Terminal decision for an approval capability. */
public enum ApprovalDecision {
  APPROVED,
  DENIED,
  TIMED_OUT,
  TARGET_DISCONNECTED,
  INITIATOR_DISCONNECTED;

  /** Returns whether this decision represents approval. */
  public boolean isApproved() {
    return this == APPROVED;
  }

  /** Returns whether this decision represents a terminal state. */
  public boolean isTerminal() {
    return true;
  }

  /**
   * Parses a raw decision string into an ApprovalDecision. Supports common action strings (e.g.
   * "confirm", "approve", "decline", "deny", "cancel").
   *
   * @param raw the raw input string
   * @return optional containing the decision, or empty if unrecognized
   */
  public static Optional<ApprovalDecision> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "confirm", "approve", "approved", "yes", "accept" -> Optional.of(APPROVED);
      case "decline", "deny", "denied", "no", "reject", "cancel" -> Optional.of(DENIED);
      case "timed_out", "timeout" -> Optional.of(TIMED_OUT);
      case "target_disconnected" -> Optional.of(TARGET_DISCONNECTED);
      case "initiator_disconnected" -> Optional.of(INITIATOR_DISCONNECTED);
      default -> Optional.empty();
    };
  }
}
