package dev.cyr1en.promptcore.logic.transform;

import java.util.Locale;

/**
 * Transformer that capitalizes the first character/code point (using root locale) and lowercases
 * the remainder.
 */
public record CapitalizeTransformer() implements Transformer {

  public static final CapitalizeTransformer INSTANCE = new CapitalizeTransformer();

  @Override
  public SingleTransformResult transform(String input, MathMode mathMode) {
    if (input == null || input.isEmpty()) {
      return SingleTransformResult.success("");
    }
    int firstCp = input.codePointAt(0);
    int charCount = Character.charCount(firstCp);
    String firstUpper =
        new String(Character.toChars(Character.toUpperCase(firstCp))).toUpperCase(Locale.ROOT);
    if (input.length() == charCount) {
      return SingleTransformResult.success(firstUpper);
    }
    String rest = input.substring(charCount).toLowerCase(Locale.ROOT);
    return SingleTransformResult.success(firstUpper + rest);
  }

  @Override
  public String name() {
    return "capitalize";
  }
}
