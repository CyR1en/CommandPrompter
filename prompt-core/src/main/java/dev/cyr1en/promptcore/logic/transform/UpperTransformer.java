package dev.cyr1en.promptcore.logic.transform;

import java.util.Locale;

/** Transformer that converts the input to uppercase using deterministic root locale. */
public record UpperTransformer() implements Transformer {

  public static final UpperTransformer INSTANCE = new UpperTransformer();

  @Override
  public SingleTransformResult transform(String input, MathMode mathMode) {
    if (input == null) {
      return SingleTransformResult.success("");
    }
    return SingleTransformResult.success(input.toUpperCase(Locale.ROOT));
  }

  @Override
  public String name() {
    return "upper";
  }
}
