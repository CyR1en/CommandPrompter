package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;

/**
 * Source from which a {@link DialogTypeConfig#type() multi-action} dialog draws its buttons.
 *
 * <p>Mirrors the {@code actions_source} field of the JSON {@code dialog_type} block. The JSON
 * schema currently only permits {@code "tab_completion"}; future additions can extend this enum.
 */
public enum ActionsSource {
  /** Buttons are populated dynamically from the player's tab-completion results. */
  @SerializedName("tab_completion")
  TAB_COMPLETION
}
