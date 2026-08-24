package dev.cyr1en.promptcore.logic.transform;

/** Transformer that strips leading and trailing whitespace from the input. */
public record TrimTransformer() implements Transformer {

  public static final TrimTransformer INSTANCE = new TrimTransformer();

  @Override
  public SingleTransformResult transform(String input, MathMode mathMode) {
    if (input == null) {
      return SingleTransformResult.success("");
    }
    return SingleTransformResult.success(input.strip());
  }

  @Override
  public String name() {
    return "trim";
  }
}
