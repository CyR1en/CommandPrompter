package dev.cyr1en.promptcore.logic.transform;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * A compiled reference segment pointing to a bound variable with an optional transformer.
 *
 * @param key the reference key (e.g. "0", "player")
 * @param transformer the transformer to execute on the bound value
 */
public record ReferenceSegment(String key, Transformer transformer) implements TemplateSegment {

  public ReferenceSegment {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(transformer, "transformer must not be null");
    if (key.isBlank()) {
      throw new IllegalArgumentException("Reference key must not be blank");
    }
  }

  public OptionalInt numericIndex() {
    try {
      return OptionalInt.of(Integer.parseInt(key));
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }
}
