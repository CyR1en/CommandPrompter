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
    var valOpt = bindings.getPlaceholder(placeholder);
    if (valOpt.isEmpty() || valOpt.get() == null) {
      throw new ConditionEvaluationException(
          "Missing or unresolvable placeholder reference: %" + placeholder + "%");
    }
    String val = valOpt.get();
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
  public void collectAnswerIndices(Set<Integer> indices) {
    // No answer indices
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
