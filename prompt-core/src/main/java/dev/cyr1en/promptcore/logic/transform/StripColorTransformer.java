package dev.cyr1en.promptcore.logic.transform;

import java.util.regex.Pattern;

/** Transformer that strips Minecraft legacy color codes, hex formatting, and MiniMessage tags. */
public record StripColorTransformer() implements Transformer {

  public static final StripColorTransformer INSTANCE = new StripColorTransformer();

  private static final Pattern LEGACY_HEX_COLOR = Pattern.compile("(?i)[§&]x([§&][0-9a-f]){6}");
  private static final Pattern HASH_HEX_COLOR = Pattern.compile("(?i)[§&]#[0-9a-f]{6}");
  private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)[§&][0-9a-fklmnor]");
  private static final Pattern MINIMESSAGE_TAG = Pattern.compile("<[^>]+>");

  @Override
  public SingleTransformResult transform(String input, MathMode mathMode) {
    if (input == null || input.isEmpty()) {
      return SingleTransformResult.success("");
    }
    String result = LEGACY_HEX_COLOR.matcher(input).replaceAll("");
    result = HASH_HEX_COLOR.matcher(result).replaceAll("");
    result = LEGACY_COLOR.matcher(result).replaceAll("");
    result = MINIMESSAGE_TAG.matcher(result).replaceAll("");
    return SingleTransformResult.success(result);
  }

  @Override
  public String name() {
    return "stripcolor";
  }
}
