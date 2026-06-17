package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;

/** What kind of input a {@link DialogRow} accepts. */
public enum InputType {
  /** Free-form text. */
  @SerializedName("text")
  TEXT,
  /** Numeric input (integer or decimal, as configured in constraints). */
  @SerializedName("number")
  NUMBER,
  /** Pick one of the supplied constraint values. */
  @SerializedName("choice")
  CHOICE
}
