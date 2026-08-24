package dev.cyr1en.promptcore;

import java.util.Objects;

/**
 * Configuration for the tag delimiter used by {@link
 * dev.cyr1en.promptcore.parser.CommandLineParser}.
 *
 * <p>Controls which tokens surround prompt tags and PCM tags in the command string.
 *
 * @param opening the opening delimiter token (e.g. {@code <})
 * @param closing the closing delimiter token (e.g. {@code >})
 * @param escape the escape token (e.g. {@code \})
 */
public record ParserConfig(String opening, String closing, String escape) {

  /** Default config using angle brackets: {@code <tag>} / {@code <!pcm>}. */
  public static final ParserConfig ANGLE_BRACKETS = new ParserConfig("<", ">", "\\");

  /** Throws {@link IllegalArgumentException} if opening, closing, or escape are null or empty. */
  public ParserConfig {
    Objects.requireNonNull(opening, "opening delimiter cannot be null");
    Objects.requireNonNull(closing, "closing delimiter cannot be null");
    Objects.requireNonNull(escape, "escape token cannot be null");
    if (opening.isEmpty() || closing.isEmpty() || escape.isEmpty()) {
      throw new IllegalArgumentException("Opening, closing, and escape must not be empty");
    }
  }

  /**
   * Creates a {@link ParserConfig} by extracting the opening and closing delimiters from an
   * argument regex.
   *
   * @param regex the regex string (e.g. {@code <.*?>})
   * @return the parsed configuration
   * @throws IllegalArgumentException if the regex is null, empty, or has a length less than 3
   * @deprecated Use {@link #ParserConfig(String, String, String)} directly.
   */
  @Deprecated
  public static ParserConfig fromArgumentRegex(String regex) {
    if (regex == null) {
      throw new IllegalArgumentException("Argument regex cannot be null");
    }
    regex = regex.trim();
    if (regex.length() < 3) {
      throw new IllegalArgumentException("Argument regex must be at least 3 characters long");
    }
    String open = String.valueOf(regex.charAt(0));
    String close = String.valueOf(regex.charAt(regex.length() - 1));
    return new ParserConfig(open, close, "\\");
  }
}
