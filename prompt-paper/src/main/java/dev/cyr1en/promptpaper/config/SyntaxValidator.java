package dev.cyr1en.promptpaper.config;

import java.util.List;
import java.util.Objects;

/**
 * Validates prompt and template syntax configuration tokens.
 *
 * <p>Ensures that syntax delimiters are non-null, non-empty, within bounds (<= 16 characters), free
 * of whitespace, control characters, and quote characters, and do not have equal or prefix overlaps
 * with each other.
 */
public final class SyntaxValidator {

  public static final int MAX_TOKEN_LENGTH = 16;

  private SyntaxValidator() {}

  /**
   * Validates a single syntax token.
   *
   * @param token the token string
   * @param name the configuration path or token name for error messages
   * @throws IllegalArgumentException if the token is null, empty, exceeds maximum length, or
   *     contains whitespace, control characters, or quotes
   */
  public static void validateToken(String token, String name) {
    Objects.requireNonNull(token, name + " must not be null");
    if (token.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    if (token.length() > MAX_TOKEN_LENGTH) {
      throw new IllegalArgumentException(
          name + " length (" + token.length() + ") exceeds maximum limit of " + MAX_TOKEN_LENGTH);
    }
    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      if (Character.isWhitespace(c)) {
        throw new IllegalArgumentException(
            name + " must not contain whitespace characters: '" + token + "'");
      }
      if (c < 0x20 || c == 0x7F) {
        throw new IllegalArgumentException(name + " must not contain control characters");
      }
      if (c == '"' || c == '\'') {
        throw new IllegalArgumentException(
            name + " must not contain quote characters: '" + token + "'");
      }
    }
  }

  /**
   * Validates all six syntax tokens individually and cross-validates that no two tokens are
   * identical or share a prefix overlap.
   *
   * @param promptOpen opening prompt tag delimiter
   * @param promptClose closing prompt tag delimiter
   * @param templateOpen opening template placeholder delimiter
   * @param templateClose closing template placeholder delimiter
   * @param transformSeparator template transformer separator
   * @param templateEscape template escape token
   * @throws IllegalArgumentException if any token is invalid or if prefix/equal overlaps exist
   */
  public static void validateCrossSyntax(
      String promptOpen,
      String promptClose,
      String templateOpen,
      String templateClose,
      String transformSeparator,
      String templateEscape) {
    record Token(String name, String value) {}
    var tokens =
        List.of(
            new Token("Syntax.Prompt.Open", promptOpen),
            new Token("Syntax.Prompt.Close", promptClose),
            new Token("Syntax.Template.Open", templateOpen),
            new Token("Syntax.Template.Close", templateClose),
            new Token("Syntax.Template.Transform-Separator", transformSeparator),
            new Token("Syntax.Template.Escape", templateEscape));
    for (var token : tokens) {
      validateToken(token.value(), token.name());
    }
    for (var token : tokens) {
      for (var other : tokens) {
        if (token != other && token.value().startsWith(other.value())) {
          throw new IllegalArgumentException(
              "Syntax configuration conflict: "
                  + token.name()
                  + " ('"
                  + token.value()
                  + "') cannot have prefix overlap with "
                  + other.name()
                  + " ('"
                  + other.value()
                  + "')");
        }
      }
    }
  }
}
