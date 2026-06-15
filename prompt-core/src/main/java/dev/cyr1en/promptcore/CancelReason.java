package dev.cyr1en.promptcore;

/**
 * Reasons a {@link PromptSession} can be cancelled.
 *
 * <p>{@link #MANUAL} — user explicitly cancelled (cancel keyword, button click).<br>
 * {@link #TIMEOUT} — session expired due to inactivity.<br>
 * {@link #GUI_EXIT} — user closed the GUI prompt without completing.<br>
 * {@link #BLANK_INPUT} — user submitted empty/blank input.
 */
public enum CancelReason {
  MANUAL,
  TIMEOUT,
  GUI_EXIT,
  BLANK_INPUT
}
