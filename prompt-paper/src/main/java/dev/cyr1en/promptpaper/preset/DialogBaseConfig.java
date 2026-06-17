package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Represents the {@code base} block of a {@link DialogPrompt}. Maps to the {@code base}
 * definition in the JSON schema — Paper's native {@code DialogBase} builder shape.
 *
 * @param body optional body elements (plain messages, item icons); may be empty
 * @param inputs optional input rows (mirrors the existing {@code dialogRow} schema)
 */
public record DialogBaseConfig(
    List<DialogBodyConfig> body, @SerializedName("inputs") List<DialogRow> inputs) {

  /** Canonical constructor. Coerces {@code null} lists to empty immutable lists. */
  public DialogBaseConfig {
    body = body == null ? List.of() : List.copyOf(body);
    inputs = inputs == null ? List.of() : List.copyOf(inputs);
  }
}
