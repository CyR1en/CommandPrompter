package dev.cyr1en.promptpaper.preset;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Objects;

/**
 * One input row inside a dialog prompt.
 *
 * <p>A row is a labelled, typed input. The {@code constraints} array is intentionally typed as
 * {@code List<JsonElement>} because the JSON schema permits mixed strings and numbers (e.g. {@code
 * ["Hacking", "Spam"]} vs {@code [1, 365]}). Downstream code can pull the values out with {@link
 * JsonElement#getAsString()} or {@link JsonElement#getAsNumber()}.
 *
 * @param label the row label shown to the player
 * @param inputType the kind of input ({@code text}, {@code number}, or {@code choice})
 * @param constraints the optional constraint list (choices for {@code choice}, range for {@code
 *     number}, ignored for {@code text}); may be {@code null} when not provided
 */
public record DialogRow(
    String label,
    @SerializedName("input_type") InputType inputType,
    List<JsonElement> constraints,
    @SerializedName("max_length") Integer maxLength,
    @SerializedName("max_lines") Integer maxLines,
    @SerializedName("width") Integer width) {

  /** Canonical constructor with null-checks. */
  public DialogRow {
    Objects.requireNonNull(label, "label must not be null");
    Objects.requireNonNull(inputType, "input_type must not be null");
    constraints = constraints == null ? List.of() : List.copyOf(constraints);
  }

  /** Legacy constructor without the new text constraints. */
  public DialogRow(String label, InputType inputType, List<JsonElement> constraints) {
    this(label, inputType, constraints, null, null, null);
  }

  /**
   * Convenience accessor: returns each constraint as a string. Numbers are rendered via {@link
   * Number#toString()} so {@code 1} becomes {@code "1"}.
   */
  @SuppressWarnings("null")
  public List<String> constraintsAsStrings() {
    return constraints.stream().map(JsonElement::getAsString).toList();
  }
}
