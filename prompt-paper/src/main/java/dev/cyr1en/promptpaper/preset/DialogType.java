package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;

/**
 * What kind of dialog a {@link DialogTypeConfig} defines inside a {@link DialogPrompt}.
 *
 * <p>Mirrors the {@code type} enum of the JSON {@code dialog_type} block.
 */
public enum DialogType {
  /** A grid of multiple action buttons, optionally sourced from tab-completion. */
  @SerializedName("multi_action")
  MULTI_ACTION,

  /** A simple confirmation / cancellation dialog with two buttons. */
  @SerializedName("confirmation")
  CONFIRMATION
}
