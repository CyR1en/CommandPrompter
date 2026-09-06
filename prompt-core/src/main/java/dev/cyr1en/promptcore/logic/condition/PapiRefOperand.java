package dev.cyr1en.promptcore.logic.condition;

import java.util.Objects;
import java.util.Set;

/** An operand referencing a PlaceholderAPI placeholder: {@code %safe_papi%}. */
public record PapiRefOperand(String placeholder) implements ValueOperand {

  public PapiRefOperand {
    Objects.requireNonNull(placeholder, "placeholder cannot be null");
    if (placeholder.isBlank()) {
      throw new IllegalArgumentException("Placeholder key cannot be blank");
    }
  }

  @Override
  public String resolve(ConditionBindings bindings) throws ConditionEvaluationException {
    Objects.requireNonNull(bindings, "bindings cannot be null");
    var val =
        bindings
            .getPlaceholder(placeholder)
            .orElseThrow(
                () ->
                    new ConditionEvaluationException(
                        "Missing or unresolvable placeholder reference: %" + placeholder + "%"));
    if (val.length() > MAX_OPERAND_LENGTH) {
      throw new ConditionEvaluationException(
          "Resolved placeholder %"
              + placeholder
              + "% length ("
              + val.length()
              + ") exceeds maximum limit of "
              + MAX_OPERAND_LENGTH);
    }
    return val;
  }

  @Override
  public void collectPapiPlaceholders(Set<String> placeholders) {
    placeholders.add(placeholder);
  }

  @Override
  public String toString() {
    return "%" + placeholder + "%";
  }
}
