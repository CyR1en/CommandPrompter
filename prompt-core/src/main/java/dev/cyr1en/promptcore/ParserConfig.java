package dev.cyr1en.promptcore;

import java.util.Objects;

/**
 * Configuration for the tag delimiter used by {@link
 * dev.cyr1en.promptcore.parser.CommandLineParser}.
 *
 * <p>Controls which characters surround prompt tags and PCM tags in the command string.
 *
 * @param opening the opening delimiter character (e.g. {@code <})
 * @param closing the closing delimiter character (e.g. {@code >})
 * @param escape the escape character (e.g. {@code \})
 */
public record ParserConfig(String opening, String closing, String escape) {

  /** Default config using angle brackets: {@code <tag>} / {@code <!pcm>}. */
  public static final ParserConfig ANGLE_BRACKETS = new ParserConfig("<", ">", "\\");

  /**
   * Throws {@link IllegalArgumentException} if opening, closing, or escape are not single
   * characters.
   */
  public ParserConfig {
    Objects.requireNonNull(opening);
    Objects.requireNonNull(closing);
    Objects.requireNonNull(escape);
    if (opening.length() != 1 || closing.length() != 1 || escape.length() != 1) {
      throw new IllegalArgumentException("Opening, closing, and escape must be single characters");
    }
  }
}
