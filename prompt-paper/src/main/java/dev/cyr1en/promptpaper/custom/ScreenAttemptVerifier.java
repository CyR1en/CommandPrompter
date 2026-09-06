package dev.cyr1en.promptpaper.custom;

/** Injected verifier callback that reads authoritative live session state AFTER a scheduler hop. */
@FunctionalInterface
public interface ScreenAttemptVerifier {

  /**
   * Verifies that the authoritative live state matches the expected verification snapshot.
   *
   * @param snapshot the expected session verification snapshot
   * @return true if the attempt is valid and authoritative, false otherwise
   */
  boolean verify(SessionVerificationSnapshot snapshot);
}
