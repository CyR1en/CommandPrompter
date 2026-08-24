package dev.cyr1en.promptpaper.preset;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable snapshot of all preset definitions loaded from {@code presets.json}.
 *
 * <p>Contains insertion-ordered, deeply immutable maps for prompts, legacy post-commands,
 * approval gates, and conditional post-commands, along with a monotonic generation number.
 *
 * <p>Safe to retain by an in-flight prompt session across registry reloads.
 *
 * @param prompts immutable map of prompt definitions keyed by id
 * @param postCommands immutable map of post-command definitions keyed by id
 * @param approvalGates immutable map of approval gate definitions keyed by id
 * @param conditionalPostCommands immutable map of conditional post-command definitions keyed by id
 * @param generation monotonic generation number for this snapshot
 */
public record PresetSnapshot(
    Map<String, PromptDefinition> prompts,
    Map<String, PostCommand> postCommands,
    Map<String, ApprovalGateDefinition> approvalGates,
    Map<String, ConditionalPostCommandDefinition> conditionalPostCommands,
    long generation) {

  public static final PresetSnapshot EMPTY =
      new PresetSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), 0L);

  /**
   * Returns an empty snapshot with generation 0.
   */
  public static PresetSnapshot empty() {
    return EMPTY;
  }

  /**
   * Canonical constructor. Ensures maps are deeply immutable and non-null while preserving
   * insertion order.
   */
  public PresetSnapshot {
    Objects.requireNonNull(prompts, "prompts must not be null");
    Objects.requireNonNull(postCommands, "postCommands must not be null");
    Objects.requireNonNull(approvalGates, "approvalGates must not be null");
    Objects.requireNonNull(conditionalPostCommands, "conditionalPostCommands must not be null");
    prompts = Collections.unmodifiableMap(new LinkedHashMap<>(prompts));
    postCommands = Collections.unmodifiableMap(new LinkedHashMap<>(postCommands));
    approvalGates = Collections.unmodifiableMap(new LinkedHashMap<>(approvalGates));
    conditionalPostCommands =
        Collections.unmodifiableMap(new LinkedHashMap<>(conditionalPostCommands));
  }

  /**
   * Convenience 4-argument constructor with generation = 0.
   */
  public PresetSnapshot(
      Map<String, PromptDefinition> prompts,
      Map<String, PostCommand> postCommands,
      Map<String, ApprovalGateDefinition> approvalGates,
      Map<String, ConditionalPostCommandDefinition> conditionalPostCommands) {
    this(prompts, postCommands, approvalGates, conditionalPostCommands, 0L);
  }

  /**
   * Looks up a prompt by its id. Returns empty if not present or if id is null.
   */
  public Optional<PromptDefinition> getPrompt(String id) {
    if (id == null) return Optional.empty();
    return Optional.ofNullable(prompts.get(id));
  }

  /**
   * Looks up a post-command by its id. Returns empty if not present or if id is null.
   */
  public Optional<PostCommand> getPostCommand(String id) {
    if (id == null) return Optional.empty();
    return Optional.ofNullable(postCommands.get(id));
  }

  /**
   * Looks up an approval gate by its id. Returns empty if not present or if id is null.
   */
  public Optional<ApprovalGateDefinition> getApprovalGate(String id) {
    if (id == null) return Optional.empty();
    return Optional.ofNullable(approvalGates.get(id));
  }

  /**
   * Looks up a conditional post-command by its id. Returns empty if not present or if id is null.
   */
  public Optional<ConditionalPostCommandDefinition> getConditionalPostCommand(String id) {
    if (id == null) return Optional.empty();
    return Optional.ofNullable(conditionalPostCommands.get(id));
  }

  /**
   * Returns an unmodifiable set of all registered prompt ids in insertion order.
   */
  public Set<String> getPromptIds() {
    return prompts.keySet();
  }

  /**
   * Returns an unmodifiable set of all registered post-command ids in insertion order.
   */
  public Set<String> getPostCommandIds() {
    return postCommands.keySet();
  }

  /**
   * Returns an unmodifiable set of all registered approval gate ids in insertion order.
   */
  public Set<String> getApprovalGateIds() {
    return approvalGates.keySet();
  }

  /**
   * Returns an unmodifiable set of all registered conditional post-command ids in insertion order.
   */
  public Set<String> getConditionalPostCommandIds() {
    return conditionalPostCommands.keySet();
  }

  /**
   * Number of registered prompt definitions.
   */
  public int promptCount() {
    return prompts.size();
  }

  /**
   * Number of registered post-command definitions.
   */
  public int postCommandCount() {
    return postCommands.size();
  }

  /**
   * Number of registered approval gate definitions.
   */
  public int approvalGateCount() {
    return approvalGates.size();
  }

  /**
   * Number of registered conditional post-command definitions.
   */
  public int conditionalPostCommandCount() {
    return conditionalPostCommands.size();
  }

  /**
   * Total number of definitions across all four kinds in this snapshot.
   */
  public int totalCount() {
    return prompts.size() + postCommands.size() + approvalGates.size() + conditionalPostCommands.size();
  }

  /**
   * Returns true if all four definition maps are empty.
   */
  public boolean isEmpty() {
    return prompts.isEmpty()
        && postCommands.isEmpty()
        && approvalGates.isEmpty()
        && conditionalPostCommands.isEmpty();
  }
}
