package dev.cyr1en.promptcore.logic.condition;

import java.util.Set;

/**
 * Immutable compiled condition expression that can be evaluated against runtime {@link
 * ConditionBindings}.
 */
public interface Condition {

  /**
   * Compiles a condition source string using default (inline) options. PlaceholderAPI references
   * are disallowed by default.
   *
   * @param source condition expression string
   * @return immutable compiled condition
   * @throws ConditionParseException if source is malformed, contains illegal tokens, or violates
   *     bounds
   */
  static Condition compile(String source) {
    return ConditionCompiler.compile(source, ConditionCompileOptions.defaultOptions());
  }

  /**
   * Compiles a condition source string with explicit compile options.
   *
   * @param source condition expression string
   * @param options compilation options governing features like PAPI references
   * @return immutable compiled condition
   * @throws ConditionParseException if source is malformed, contains illegal tokens, or violates
   *     bounds
   */
  static Condition compile(String source, ConditionCompileOptions options) {
    return ConditionCompiler.compile(source, options);
  }

  /**
   * Evaluates this condition against the provided runtime bindings.
   *
   * @param bindings runtime bindings supplying answer values and placeholders
   * @return boolean evaluation result
   * @throws ConditionEvaluationException if evaluation fails closed due to missing values, type
   *     errors, or length bounds
   */
  boolean evaluate(ConditionBindings bindings) throws ConditionEvaluationException;

  /** Returns the original condition source string. */
  String source();

  /** Returns the root node of the compiled AST. */
  ConditionNode root();

  /** Returns the maximum nesting depth of the AST. */
  int depth();

  /** Returns the total number of AST nodes. */
  int nodeCount();

  /** Returns true if this condition contains any PlaceholderAPI references. */
  boolean hasPapiRefs();

  /** Returns an unmodifiable set of all zero-based answer indices referenced by this condition. */
  Set<Integer> answerIndices();

  /**
   * Returns an unmodifiable set of all PlaceholderAPI placeholder keys referenced by this
   * condition.
   */
  Set<String> papiPlaceholders();
}
