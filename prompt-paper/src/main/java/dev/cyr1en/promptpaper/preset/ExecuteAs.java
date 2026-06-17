package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;

/** The execution context of a {@link PostCommand}. */
public enum ExecuteAs {
  /** Run as the server console (no player attached). */
  @SerializedName("console")
  CONSOLE,
  /** Run as the player who triggered the prompt flow. */
  @SerializedName("player")
  PLAYER
}
