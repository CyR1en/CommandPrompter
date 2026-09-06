package dev.cyr1en.promptpaper.screen.confirmation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Immutable binding of a confirmation prompt nonce to its session context and callback.
 *
 * @param nonce the cryptographic one-time token
 * @param playerUuid the player UUID that owns this prompt
 * @param incarnation the monotonic session incarnation id
 * @param generation the monotonic prompt generation id
 * @param promptIndex the index of the prompt tag within the command
 * @param expiresAt the UTC expiration timestamp
 * @param callback the decision consumer called on successful completion
 */
public record ConfirmationBinding(
    String nonce,
    UUID playerUuid,
    long incarnation,
    long generation,
    int promptIndex,
    Instant expiresAt,
    Consumer<ConfirmationDecision> callback) {

  public ConfirmationBinding {
    Objects.requireNonNull(nonce, "nonce must not be null");
    Objects.requireNonNull(playerUuid, "playerUuid must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }

  /**
   * Checks if this binding is expired relative to the given timestamp.
   *
   * @param now current instant
   * @return true if now is after expiresAt
   */
  public boolean isExpired(Instant now) {
    return now.isAfter(expiresAt);
  }
}
