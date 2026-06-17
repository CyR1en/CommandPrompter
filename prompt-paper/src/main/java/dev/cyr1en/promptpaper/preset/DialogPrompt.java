package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

/**
 * Dialog-prompt definition: an interactive dialog matching Paper's native {@code Dialog}
 * builder shape ({@code DialogBase} + {@code DialogType}).
 *
 * <p>This is the post-refactor shape defined in
 * {@code docs/superpowers/specs/2026-06-16-dialog-ui-refactor-spec.html}. It replaces the
 * legacy flat {@code rows} array with a {@link #base()} + {@link #dialogType()} pair so the
 * preset definition exactly mirrors Paper's builder API.
 *
 * @param type the discriminator value, always {@code "dialog"}
 * @param id the unique identifier
 * @param title the dialog title (rendered into Paper's {@code title} builder field)
 * @param base optional {@code base} block (body elements + input rows); may be {@code null}
 * @param dialogType the required {@code dialog_type} block describing the action layout
 * @param sanitize whether to strip color codes from the player's input
 */
public record DialogPrompt(
    String type,
    String id,
    String title,
    DialogBaseConfig base,
    @SerializedName("dialog_type") DialogTypeConfig dialogType,
    boolean sanitize)
    implements PromptDefinition {

  /** Canonical constructor. */
  public DialogPrompt {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(dialogType, "dialog_type must not be null");
    if (!"dialog".equals(type)) {
      throw new IllegalArgumentException("DialogPrompt.type must be \"dialog\", got: " + type);
    }
  }
}
