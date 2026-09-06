package dev.cyr1en.promptcore.logic.condition;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable concrete implementation of {@link Condition}. */
public final class CompiledCondition implements Condition {

  private final String source;
  private final ConditionNode root;
  private final int depth;
  private final int nodeCount;
  private final Set<Integer> answerIndices;
  private final Set<String> papiPlaceholders;

  public CompiledCondition(String source, ConditionNode root) {
    this.source = Objects.requireNonNull(source, "source cannot be null");
    this.root = Objects.requireNonNull(root, "root node cannot be null");
    this.depth = root.depth();
    this.nodeCount = root.nodeCount();

    Set<Integer> answers = new HashSet<>();
    root.collectAnswerIndices(answers);
    this.answerIndices = Set.copyOf(answers);

    Set<String> placeholders = new HashSet<>();
    root.collectPapiPlaceholders(placeholders);
    this.papiPlaceholders = Set.copyOf(placeholders);
  }

  @Override
  public boolean evaluate(ConditionBindings bindings) throws ConditionEvaluationException {
    Objects.requireNonNull(bindings, "bindings cannot be null");
    return root.evaluate(bindings);
  }

  @Override
  public String source() {
    return source;
  }

  @Override
  public ConditionNode root() {
    return root;
  }

  @Override
  public int depth() {
    return depth;
  }

  @Override
  public int nodeCount() {
    return nodeCount;
  }

  @Override
  public boolean hasPapiRefs() {
    return !papiPlaceholders.isEmpty();
  }

  @Override
  public Set<Integer> answerIndices() {
    return answerIndices;
  }

  @Override
  public Set<String> papiPlaceholders() {
    return papiPlaceholders;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof CompiledCondition other)) return false;
    return source.equals(other.source);
  }

  @Override
  public int hashCode() {
    return source.hashCode();
  }

  @Override
  public String toString() {
    return "CompiledCondition[" + source + "]";
  }
}
