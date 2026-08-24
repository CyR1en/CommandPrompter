package dev.cyr1en.promptcore.logic.transform;

/** Identity transformer that passes the bound value unchanged. */
public record NoOpTransformer() implements Transformer {

  public static final NoOpTransformer INSTANCE = new NoOpTransformer();

  @Override
  public SingleTransformResult transform(String input, MathMode mathMode) {
    return SingleTransformResult.success(input == null ? "" : input);
  }

  @Override
  public String name() {
    return "none";
  }
}
