package dev.cyr1en.promptpaper.approval;

import dev.cyr1en.promptpaper.execution.runtime.ExecutionId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable approval capability bound to a cryptographic nonce, exact execution ID,
 * gate preset ID, initiator UUID + incarnation, target approver UUID, and expiration.
 */
public record ApprovalCapability(
    String nonce,
    ExecutionId executionId,
    String gateId,
    UUID initiator,
    long initiatorIncarnation,
    UUID target,
    Instant expiresAt) {

  public static final int MAX_GATE_ID_LENGTH = 64;

  public ApprovalCapability {
    Objects.requireNonNull(nonce, "nonce must not be null");
    if (nonce.isBlank()) {
      throw new IllegalArgumentException("nonce must not be blank");
    }
    if (!nonce.startsWith(ApprovalCapabilityRegistry.NONCE_PREFIX)) {
      throw new IllegalArgumentException(
          "nonce must start with '" + ApprovalCapabilityRegistry.NONCE_PREFIX + "'");
    }
    Objects.requireNonNull(executionId, "executionId must not be null");
    Objects.requireNonNull(gateId, "gateId must not be null");
    if (gateId.isBlank() || gateId.length() > MAX_GATE_ID_LENGTH) {
      throw new IllegalArgumentException(
          "gateId must be between 1 and " + MAX_GATE_ID_LENGTH + " characters");
    }
    Objects.requireNonNull(initiator, "initiator must not be null");
    Objects.requireNonNull(target, "target must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }

  /**
   * Checks whether this capability has expired relative to the given instant.
   *
   * @param now current instant
   * @return true if expired
   */
  public boolean isExpired(Instant now) {
    Objects.requireNonNull(now, "now must not be null");
    return !now.isBefore(expiresAt);
  }

  /**
   * Checks whether the given responder UUID matches the bound target approver.
   *
   * @param responder responder UUID
   * @return true if responder matches target
   */
  public boolean matchesResponder(UUID responder) {
    return Objects.equals(this.target, responder);
  }

  /**
   * Truncates the nonce to a safe prefix suitable for logging or diagnostics without leaking the raw token.
   *
   * @return safe masked nonce
   */
  public String safeMaskedNonce() {
    if (nonce.length() <= 8) {
      return ApprovalCapabilityRegistry.NONCE_PREFIX + "***";
    }
    return nonce.substring(0, 8) + "...";
  }

  @Override
  public String toString() {
    return "ApprovalCapability["
        + "nonce=" + safeMaskedNonce()
        + ", executionId=" + executionId
        + ", gateId=" + gateId
        + ", initiator=" + initiator
        + ", initiatorIncarnation=" + initiatorIncarnation
        + ", target=" + target
        + ", expiresAt=" + expiresAt
        + "]";
  }
}
