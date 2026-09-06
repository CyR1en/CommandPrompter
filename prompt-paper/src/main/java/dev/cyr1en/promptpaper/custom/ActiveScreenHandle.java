package dev.cyr1en.promptpaper.custom;

import dev.cyr1en.promptui.InputScreen;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal handle storing active screen ownership, session metadata, attempt token, and associated
 * custom provider token/handle (if any).
 *
 * @param playerUuid the player UUID owning this active screen
 * @param screen the active {@link InputScreen} (may be wrapped)
 * @param attemptToken unique monotonic screen-attempt sequence token
 * @param incarnation session incarnation at open time
 * @param generation session prompt generation at open time
 * @param promptIndex current prompt index in multi-prompt sequence (-1 if not applicable)
 * @param customHandle provider registration handle / token (null for built-in/preset screens)
 */
public record ActiveScreenHandle(
    UUID playerUuid,
    InputScreen screen,
    long attemptToken,
    long incarnation,
    long generation,
    int promptIndex,
    CustomScreenHandle customHandle) {
  public ActiveScreenHandle {
    Objects.requireNonNull(playerUuid, "playerUuid");
    Objects.requireNonNull(screen, "screen");
  }

  /** Returns the unique provider ID if this is a custom screen, or {@code null} otherwise. */
  public Long providerId() {
    return customHandle != null ? customHandle.providerId() : null;
  }

  /** Returns whether this active screen is provided by a custom third-party provider. */
  public boolean isCustom() {
    return customHandle != null;
  }

  /** Returns an optional containing the {@link CustomScreenHandle} if this is a custom screen. */
  public Optional<CustomScreenHandle> optionalCustomHandle() {
    return Optional.ofNullable(customHandle);
  }
}
