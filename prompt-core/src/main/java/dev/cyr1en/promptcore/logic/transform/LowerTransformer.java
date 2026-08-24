package dev.cyr1en.promptcore.logic.transform;

import java.util.Locale;

/** Transformer that converts the input to lowercase using deterministic root locale. */
public record LowerTransformer() implements Transformer {

  public static final LowerTransformer INSTANCE = new LowerTransformer();

  @Override
  public SingleTransformResult transform(String input, MathMode mathMode) {
    if (input == null) {
      return SingleTransformResult.success("");
    }
    return SingleTransformResult.success(input.toLowerCase(Locale.ROOT));
  }

  @Override
  public String name() {
    return "lower";
  }
}
