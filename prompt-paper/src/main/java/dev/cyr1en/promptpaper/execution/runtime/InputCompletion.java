package dev.cyr1en.promptpaper.execution.runtime;

import dev.cyr1en.promptcore.PostCommandMeta;
import dev.cyr1en.promptcore.SessionResult;
import dev.cyr1en.promptcore.plan.ExecutionPlanDefinition;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable completion record captured at the end of input collection before plan execution.
 *
 * <p>Contains the initiator UUID, session incarnation token, final session generation token,
 * collected answers, assembled command / compiled plan, captured {@link PresetSnapshot} at session
 * inception, immutable {@link DispatchContextSnapshot}, and immutable resolved {@link
 * PostCommandMeta} list.
 *
 * @param initiatorUuid player UUID who initiated the prompt session
 * @param incarnation session incarnation token
 * @param finalGeneration session generation token at completion
 * @param answers collected answer strings in prompt order
 * @param assembledCommand assembled primary command string (nullable if plan has no primary
 *     command)
 * @param compiledPlan compiled execution plan definition (nullable if only raw assembled command
 *     exists)
 * @param capturedPresetSnapshot immutable snapshot of presets captured at session inception
 * @param dispatchContext immutable dispatch context snapshot
 * @param postCommands immutable full PCM source list
 */
public record InputCompletion(
    UUID initiatorUuid,
    long incarnation,
    long finalGeneration,
    List<String> answers,
    String assembledCommand,
    ExecutionPlanDefinition compiledPlan,
    PresetSnapshot capturedPresetSnapshot,
    DispatchContextSnapshot dispatchContext,
    List<PostCommandMeta> postCommands) {

  public InputCompletion {
    Objects.requireNonNull(initiatorUuid, "initiatorUuid must not be null");
    Objects.requireNonNull(answers, "answers must not be null");
    Objects.requireNonNull(capturedPresetSnapshot, "capturedPresetSnapshot must not be null");
    Objects.requireNonNull(dispatchContext, "dispatchContext must not be null");
    answers = List.copyOf(answers);
    postCommands = postCommands != null ? List.copyOf(postCommands) : List.of();

    if (assembledCommand == null && compiledPlan == null) {
      throw new IllegalArgumentException("assembledCommand and compiledPlan cannot both be null");
    }
  }

  public InputCompletion(
      UUID initiatorUuid,
      long incarnation,
      long finalGeneration,
      List<String> answers,
      String assembledCommand,
      ExecutionPlanDefinition compiledPlan,
      PresetSnapshot capturedPresetSnapshot,
      DispatchContextSnapshot dispatchContext) {
    this(
        initiatorUuid,
        incarnation,
        finalGeneration,
        answers,
        assembledCommand,
        compiledPlan,
        capturedPresetSnapshot,
        dispatchContext,
        List.of());
  }

  /** Returns the assembled command if present. */
  public Optional<String> getAssembledCommand() {
    return Optional.ofNullable(assembledCommand);
  }

  /** Returns the compiled plan definition if present. */
  public Optional<ExecutionPlanDefinition> getCompiledPlan() {
    return Optional.ofNullable(compiledPlan);
  }

  /**
   * Convenience factory to build an InputCompletion from SessionResult, captured context, and PCMs.
   */
  public static InputCompletion of(
      UUID initiatorUuid,
      long incarnation,
      long finalGeneration,
      SessionResult sessionResult,
      ExecutionPlanDefinition compiledPlan,
      PresetSnapshot presetSnapshot,
      DispatchContextSnapshot dispatchContext,
      List<PostCommandMeta> postCommands) {
    Objects.requireNonNull(sessionResult, "sessionResult must not be null");
    List<PostCommandMeta> effectivePcms;
    if (postCommands != null && !postCommands.isEmpty()) {
      effectivePcms = postCommands;
    } else {
      List<PostCommandMeta> combined = new ArrayList<>(sessionResult.onCompleteCmds());
      combined.addAll(sessionResult.onCancelCmds());
      effectivePcms = combined;
    }
    return new InputCompletion(
        initiatorUuid,
        incarnation,
        finalGeneration,
        sessionResult.answers(),
        sessionResult.assembledCommand(),
        compiledPlan,
        presetSnapshot,
        dispatchContext,
        effectivePcms);
  }

  /** Convenience factory to build an InputCompletion from SessionResult and captured context. */
  public static InputCompletion of(
      UUID initiatorUuid,
      long incarnation,
      long finalGeneration,
      SessionResult sessionResult,
      ExecutionPlanDefinition compiledPlan,
      PresetSnapshot presetSnapshot,
      DispatchContextSnapshot dispatchContext) {
    return of(
        initiatorUuid,
        incarnation,
        finalGeneration,
        sessionResult,
        compiledPlan,
        presetSnapshot,
        dispatchContext,
        null);
  }
}
