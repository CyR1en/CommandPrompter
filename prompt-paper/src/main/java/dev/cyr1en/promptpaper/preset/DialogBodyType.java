package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;

/**
 * What kind of body element a {@link DialogBodyConfig} represents inside a {@link
 * DialogPrompt#getBase() base} block.
 *
 * <p>Mirrors the {@code type} enum of an item inside the JSON {@code base.body} array.
 */
public enum DialogBodyType {
  /** A plain text message rendered into the dialog body. */
  @SerializedName("plain_message")
  PLAIN_MESSAGE,

  /** A Bukkit material rendered as an item icon in the dialog body. */
  @SerializedName("item")
  ITEM
}
