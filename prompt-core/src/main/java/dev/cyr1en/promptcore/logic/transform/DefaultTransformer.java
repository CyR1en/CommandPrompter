package dev.cyr1en.promptcore.logic.transform;

import java.util.Objects;

/** Transformer that substitutes a fallback value if the bound input is missing or blank. */
public record DefaultTransformer(String defaultValue) implements Transformer {

  public DefaultTransformer {
    Objects.requireNonNull(defaultValue, "defaultValue must not be null");
  }

  @Override
  public SingleTransformResult transform(String input, MathMode mathMode) {
    if (input == null || input.isBlank()) {
      return SingleTransformResult.success(defaultValue);
    }
    return SingleTransformResult.success(input);
  }

  @Override
  public String name() {
    return "default";
  }
}
