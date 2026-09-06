package dev.cyr1en.promptcore.plan;

import dev.cyr1en.promptcore.logic.transform.CompiledTemplate;
import java.util.List;
import java.util.Objects;

/**
 * Platform-neutral immutable compiled definition of a command execution plan.
 *
 * <p>Contains the pre-compiled primary command template, ordered pre-dispatch gate specifications,
 * and ordered post-action specifications.
 *
 * <p>All collections are defensively copied and immutable. Executable templates are stored as
 * compiled templates.
 */
public record ExecutionPlanDefinition(
    CompiledTemplate primaryCommandTemplate,
    List<PreDispatchGateSpec> preDispatchGates,
    List<PostActionSpec> postActions) {

  public static final int MAX_GATES = 16;
  public static final int MAX_POST_ACTIONS = 64;

  public ExecutionPlanDefinition {
    Objects.requireNonNull(primaryCommandTemplate, "primaryCommandTemplate must not be null");
    Objects.requireNonNull(preDispatchGates, "preDispatchGates must not be null");
    Objects.requireNonNull(postActions, "postActions must not be null");

    if (preDispatchGates.size() > MAX_GATES) {
      throw new IllegalArgumentException(
          "preDispatchGates count exceeds limit of " + MAX_GATES + ": " + preDispatchGates.size());
    }

    int totalPostActionNodes = 0;
    for (PostActionSpec action : postActions) {
      Objects.requireNonNull(action, "postActions elements must not be null");
      totalPostActionNodes += action.nodeCount();
    }

    if (totalPostActionNodes > MAX_POST_ACTIONS) {
      throw new IllegalArgumentException(
          "total postActions node count exceeds limit of "
              + MAX_POST_ACTIONS
              + ": "
              + totalPostActionNodes);
    }

    preDispatchGates = List.copyOf(preDispatchGates);
    postActions = List.copyOf(postActions);
  }

  public boolean hasGates() {
    return !preDispatchGates.isEmpty();
  }

  public boolean hasPostActions() {
    return !postActions.isEmpty();
  }

  public int gateCount() {
    return preDispatchGates.size();
  }

  public int postActionCount() {
    return postActions.size();
  }

  public int totalPostActionNodes() {
    int total = 0;
    for (PostActionSpec action : postActions) {
      total += action.nodeCount();
    }
    return total;
  }
}
