package dev.cyr1en.promptcore.logic.condition;

import java.util.Objects;
import java.util.Set;

/** An operand referencing a collected answer by zero-based index: {@code {N}}. */
public record AnswerRefOperand(int index) implements ValueOperand {

  public AnswerRefOperand {
    if (index < 0) {
      throw new IllegalArgumentException("Answer index must be non-negative: " + index);
    }
  }

  @Override
  public String resolve(ConditionBindings bindings) throws ConditionEvaluationException {
    Objects.requireNonNull(bindings, "bindings cannot be null");
    var answer =
        bindings
            .getAnswer(index)
            .orElseThrow(
                () ->
                    new ConditionEvaluationException(
                        "Missing or unresolvable answer reference: {" + index + "}"));
    if (answer.length() > MAX_OPERAND_LENGTH) {
      throw new ConditionEvaluationException(
          "Resolved answer {"
              + index
              + "} length ("
              + answer.length()
              + ") exceeds maximum limit of "
              + MAX_OPERAND_LENGTH);
    }
    return answer;
  }

  @Override
  public void collectAnswerIndices(Set<Integer> indices) {
    indices.add(index);
  }

  @Override
  public String toString() {
    return "{" + index + "}";
  }
}
