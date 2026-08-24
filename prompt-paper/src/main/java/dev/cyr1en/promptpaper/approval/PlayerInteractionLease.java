package dev.cyr1en.promptpaper.approval;

import dev.cyr1en.promptpaper.execution.runtime.ExecutionId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable lease representing exclusive interaction ownership of a player.
 *
 * <p>Supports both approval gate execution leases and prompt session claims.</p>
 */
public record PlayerInteractionLease(
    UUID player,
    ExecutionId executionId,
    Long promptIncarnation,
    ClaimType claimType,
    Instant acquiredAt,
    Instant expiresAt) {

  public enum ClaimType {
    APPROVAL,
    PROMPT
  }

  public PlayerInteractionLease {
    Objects.requireNonNull(player, "player must not be null");
    Objects.requireNonNull(claimType, "claimType must not be null");
    Objects.requireNonNull(acquiredAt, "acquiredAt must not be null");
    if (expiresAt != null && expiresAt.isBefore(acquiredAt)) {
      throw new IllegalArgumentException("expiresAt cannot be before acquiredAt");
    }
    if (claimType == ClaimType.APPROVAL && executionId == null) {
      throw new IllegalArgumentException("executionId must not be null for approval lease");
    }
    if (claimType == ClaimType.PROMPT && promptIncarnation == null) {
      throw new IllegalArgumentException("promptIncarnation must not be null for prompt claim");
    }
  }

  /**
   * Backwards compatible constructor for approval leases.
   */
  public PlayerInteractionLease(
      UUID player,
      ExecutionId executionId,
      Instant acquiredAt,
      Instant expiresAt) {
    this(
        player,
        Objects.requireNonNull(executionId, "executionId must not be null"),
        null,
        ClaimType.APPROVAL,
        acquiredAt,
        Objects.requireNonNull(expiresAt, "expiresAt must not be null"));
  }

  public static PlayerInteractionLease forApproval(
      UUID player, ExecutionId executionId, Instant acquiredAt, Instant expiresAt) {
    return new PlayerInteractionLease(
        player,
        Objects.requireNonNull(executionId, "executionId must not be null"),
        null,
        ClaimType.APPROVAL,
        acquiredAt,
        Objects.requireNonNull(expiresAt, "expiresAt must not be null"));
  }

  public static PlayerInteractionLease forPrompt(
      UUID player, long promptIncarnation, Instant acquiredAt) {
    return new PlayerInteractionLease(
        player, null, promptIncarnation, ClaimType.PROMPT, acquiredAt, null);
  }

  public static PlayerInteractionLease forPrompt(
      UUID player, long promptIncarnation, Instant acquiredAt, Instant expiresAt) {
    return new PlayerInteractionLease(
        player, null, promptIncarnation, ClaimType.PROMPT, acquiredAt, expiresAt);
  }

  public boolean isPrompt() {
    return claimType == ClaimType.PROMPT;
  }

  public boolean isApproval() {
    return claimType == ClaimType.APPROVAL;
  }

  /**
   * Checks whether this lease has expired relative to the given instant.
   *
   * @param now current instant
   * @return true if expired
   */
  public boolean isExpired(Instant now) {
    Objects.requireNonNull(now, "now must not be null");
    if (expiresAt == null) {
      return false;
    }
    return !now.isBefore(expiresAt);
  }
}
