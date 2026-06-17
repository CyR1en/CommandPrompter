package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;

/** When a post-command should fire, relative to the prompt-flow lifecycle. */
public enum ExecutionPolicy {
  /** Fire after the prompt flow completes successfully. */
  @SerializedName("on_complete")
  ON_COMPLETE,
  /** Fire after the prompt flow is cancelled. */
  @SerializedName("on_cancel")
  ON_CANCEL
}
