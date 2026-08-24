package dev.cyr1en.promptcore.logic.condition;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Pure binding interface supplying resolved answer values and optional PlaceholderAPI values to a
 * condition evaluator. Values are supplied as opaque data strings and are never reparsed.
 */
@FunctionalInterface
public interface ConditionBindings {

  /**
   * Resolves the collected answer at the given zero-based index.
   *
   * @param index zero-based answer index
   * @return optional containing the answer string, or empty if unresolvable/not present
   */
  Optional<String> getAnswer(int index);

  /**
   * Resolves the PlaceholderAPI value for the given placeholder key (without enclosing % signs).
   *
   * @param placeholder placeholder key (e.g. "player_name", "vault_eco_balance")
   * @return optional containing the resolved string value, or empty if unresolvable/not present
   */
  default Optional<String> getPlaceholder(String placeholder) {
    return Optional.empty();
  }

  /**
   * Creates a binding instance from a list of collected answers.
   *
   * @param answers list of answer strings
   * @return bindings backed by the provided list
   */
  static ConditionBindings ofAnswers(List<String> answers) {
    List<String> copy = answers == null ? List.of() : List.copyOf(answers);
    return index ->
        (index >= 0 && index < copy.size())
            ? Optional.ofNullable(copy.get(index))
            : Optional.empty();
  }

  /**
   * Creates a binding instance from varargs of collected answers.
   *
   * @param answers answer strings
   * @return bindings backed by the provided answers
   */
  static ConditionBindings ofAnswers(String... answers) {
    return ofAnswers(answers == null ? List.of() : List.of(answers));
  }

  /**
   * Creates a binding instance from answers and placeholder key-value mappings.
   *
   * @param answers list of answer strings
   * @param placeholders map of placeholder keys (without %) to resolved values
   * @return bindings backed by the provided answers and placeholders
   */
  static ConditionBindings of(List<String> answers, Map<String, String> placeholders) {
    List<String> answersCopy = answers == null ? List.of() : List.copyOf(answers);
    Map<String, String> placeholdersCopy =
        placeholders == null ? Map.of() : Map.copyOf(placeholders);
    return new ConditionBindings() {
      @Override
      public Optional<String> getAnswer(int index) {
        if (index >= 0 && index < answersCopy.size()) {
          return Optional.ofNullable(answersCopy.get(index));
        }
        return Optional.empty();
      }

      @Override
      public Optional<String> getPlaceholder(String placeholder) {
        if (placeholder == null) {
          return Optional.empty();
        }
        return Optional.ofNullable(placeholdersCopy.get(placeholder));
      }
    };
  }

  /**
   * Creates a binding instance from custom resolver functions.
   *
   * @param answerResolver function resolving answer index to optional string
   * @param placeholderResolver function resolving placeholder name to optional string
   * @return custom bindings
   */
  static ConditionBindings custom(
      IntFunction<Optional<String>> answerResolver,
      Function<String, Optional<String>> placeholderResolver) {
    Objects.requireNonNull(answerResolver, "answerResolver cannot be null");
    Objects.requireNonNull(placeholderResolver, "placeholderResolver cannot be null");
    return new ConditionBindings() {
      @Override
      public Optional<String> getAnswer(int index) {
        return answerResolver.apply(index);
      }

      @Override
      public Optional<String> getPlaceholder(String placeholder) {
        return placeholderResolver.apply(placeholder);
      }
    };
  }

  /** Creates an empty binding instance with no answers and no placeholders. */
  static ConditionBindings empty() {
    return index -> Optional.empty();
  }
}
