package dev.cyr1en.promptcore;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable model representing parsed confirmation prompt syntax.
 *
 * @param promptText the main prompt message to display
 * @param confirmLabel the custom confirm button label, or null for default
 * @param cancelLabel the custom cancel button label, or null for default
 * @param mode explicit presentation mode override, or null for default
 * @param valueMode whether value mode is enabled (submits "true"/"false" instead of 0-arity gating)
 * @param soundKey optional namespaced sound key to play on open, or null for none
 */
public record ConfirmationSyntax(
    String promptText,
    String confirmLabel,
    String cancelLabel,
    ConfirmationMode mode,
    boolean valueMode,
    String soundKey) {

  public ConfirmationSyntax {
    Objects.requireNonNull(promptText, "promptText cannot be null");
  }

  public Optional<String> optionalConfirmLabel() {
    return Optional.ofNullable(confirmLabel);
  }

  public Optional<String> optionalCancelLabel() {
    return Optional.ofNullable(cancelLabel);
  }

  public Optional<ConfirmationMode> optionalMode() {
    return Optional.ofNullable(mode);
  }

  public Optional<String> optionalSoundKey() {
    return Optional.ofNullable(soundKey);
  }
}
