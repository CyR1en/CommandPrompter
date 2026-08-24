package dev.cyr1en.promptcore.logic.transform;

import java.util.Objects;

/**
 * Defines the syntax delimiters for compiling template expressions.
 *
 * @param open the opening token for placeholders (e.g. {@code "{"})
 * @param close the closing token for placeholders (e.g. {@code "}"})
 * @param transformSeparator the separator between reference key and transformer specification
 *     (e.g. {@code ":"})
 * @param escape the escape token (e.g. {@code "\\"})
 */
public record TemplateSyntax(String open, String close, String transformSeparator, String escape) {

  public static final int MAX_TOKEN_LENGTH = 16;

  public static final TemplateSyntax DEFAULT = new TemplateSyntax("{", "}", ":", "\\");
  public static final TemplateSyntax LEGACY_DEFAULT = DEFAULT;

  public TemplateSyntax {
    validateToken(open, "open");
    validateToken(close, "close");
    validateToken(transformSeparator, "transformSeparator");
    validateToken(escape, "escape");

    String[] tokens = new String[] {open, close, transformSeparator, escape};
    String[] names = new String[] {"open", "close", "transformSeparator", "escape"};

    for (int i = 0; i < tokens.length; i++) {
      for (int j = 0; j < tokens.length; j++) {
        if (i != j && tokens[i].startsWith(tokens[j])) {
          throw new IllegalArgumentException(
              "Template syntax token "
                  + names[i]
                  + " ('"
                  + tokens[i]
                  + "') cannot have prefix overlap with "
                  + names[j]
                  + " ('"
                  + tokens[j]
                  + "')");
        }
      }
    }
  }

  private static void validateToken(String token, String name) {
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
}
