package dev.cyr1en.promptui;

/**
 * Marker interface for dialog-based screens. Reserved for future use; no current {@link
 * ScreenProvider} implementation creates dialogs because Paper has a native Dialog API that does
 * not require a custom screen wrapper.
 *
 * <p>Kept in the SPI for forward compatibility.
 *
 * <p>Dialog screens expose the number of answers their concrete flow submits when confirmed. This
 * is the arity the session manager must use to decode the result payload and record per-prompt
 * answer counts — it is not derivable from the parsed tag shape for JSON dialog presets (which may
 * submit 0, 1, or N answers) or for inline dialogs with layout-only rows.
 */
public interface DialogScreen extends InputScreen {

  /**
   * The number of answers this dialog submits when confirmed.
   *
   * <p>Decided when the concrete flow is built/opened (e.g. a tab-completion fallback injects an
   * extra input); implementations should cache the value. Returns {@code -1} when the flow has not
   * been opened yet or the screen is not an answer-bearing dialog.
   */
  default int effectiveAnswerCount() {
    return -1;
  }
}
