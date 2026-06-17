package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

/**
 * Represents one button that a {@link DialogPrompt} can render, mapping to the
 * {@code actionButton} definition in the JSON schema.
 *
 * <p>Used by both {@link DialogTypeConfig#confirmAction() confirm} /
 * {@link DialogTypeConfig#cancelAction() cancel} / {@link DialogTypeConfig#exitAction() exit}
 * buttons and by the entries of {@link DialogTypeConfig#actions()}.
 *
 * @param label the button label shown to the player; required and non-empty
 * @param tooltip optional hover tooltip text
 * @param returnValue optional value the dialog returns to the flow when this button is
 *     activated (e.g. a command fragment or a placeholder value)
 */
public record ActionButtonConfig(
    String label,
    String tooltip,
    @SerializedName("return") String returnValue) {

  /** Canonical constructor with non-null / non-empty enforcement for {@code label}. */
  public ActionButtonConfig {
    Objects.requireNonNull(label, "label must not be null");
    if (label.isEmpty()) {
      throw new IllegalArgumentException("label must not be empty");
    }
  }
}
