package dev.cyr1en.promptpaper.screen.confirmation;

/**
 * Safe diagnostic reason for rejecting a confirmation nonce response.
 *
 * <p>Never contains player-derived text or full secret nonce tokens.
 */
public enum RejectionReason {
  NONCE_NOT_FOUND,
  EXPIRED,
  PLAYER_MISMATCH,
  INCARNATION_MISMATCH,
  GENERATION_MISMATCH,
  PROMPT_INDEX_MISMATCH,
  INVALID_DECISION,
  RATE_LIMITED
}
