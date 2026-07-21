package dev.cyr1en.promptcore;

import java.util.Objects;

/**
 * Configuration for the on-screen title wrapper that can be attached to any prompt via the inline
 * {@code -t} flag (e.g. {@code <a:Why? -t:"Main"|"Sub"|70>}).
 *
 * <p>The title is shown to the player as an Adventure API {@code Title} for {@code ticks} ticks
 * before the underlying prompt screen is opened.
 *
 * @param main the main title text; an empty string signals "use the prompt's display text"
 * @param sub the optional subtitle text; {@code null} when not provided
 * @param ticks the number of ticks the title stays on screen; {@code null} defaults to 70
 */
public record TitleConfig(String main, String sub, Integer ticks) {

  /** Compact constructor that validates {@code main} is non-null. */
  public TitleConfig {
    Objects.requireNonNull(main, "main must not be null (use empty string for default)");
  }

  /**
   * Whether this config was produced by the standalone {@code -t} flag (no parameters). When {@code
   * true}, the caller should inject the prompt's display text as the main title.
   */
  public boolean useDisplayTextAsMain() {
    return main.isEmpty() && sub == null && ticks == null;
  }
}
