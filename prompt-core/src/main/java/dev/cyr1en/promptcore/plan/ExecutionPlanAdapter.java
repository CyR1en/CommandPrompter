package dev.cyr1en.promptcore.plan;

import dev.cyr1en.promptcore.ParsedCommand;
import dev.cyr1en.promptcore.PostCommandMeta;
import dev.cyr1en.promptcore.logic.transform.CompiledTemplate;
import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adapter between legacy {@link ParsedCommand}/{@link PostCommandMeta} and modern {@link
 * ExecutionPlanDefinition}/{@link PostActionSpec}.
 *
 * <p>Boundary adapter compiles raw template strings into {@link CompiledTemplate} instances exactly
 * once.
 */
public final class ExecutionPlanAdapter {

  private ExecutionPlanAdapter() {}

  /**
   * Converts a {@link ParsedCommand} into an {@link ExecutionPlanDefinition} using the pre-dispatch
   * gates recorded on the parsed command.
   *
   * @param parsedCommand the parsed command to convert
   * @return the immutable execution plan definition
   */
  public static ExecutionPlanDefinition fromParsedCommand(ParsedCommand parsedCommand) {
    Objects.requireNonNull(parsedCommand, "parsedCommand must not be null");
    return fromParsedCommand(
        parsedCommand,
        parsedCommand.preDispatchGates(),
        dev.cyr1en.promptcore.logic.transform.TemplateSyntax.DEFAULT);
  }

  public static ExecutionPlanDefinition fromParsedCommand(
      ParsedCommand parsedCommand, dev.cyr1en.promptcore.logic.transform.TemplateSyntax syntax) {
    Objects.requireNonNull(parsedCommand, "parsedCommand must not be null");
    return fromParsedCommand(parsedCommand, parsedCommand.preDispatchGates(), syntax);
  }

  public static ExecutionPlanDefinition fromParsedCommand(
      ParsedCommand parsedCommand, List<PreDispatchGateSpec> gates) {
    return fromParsedCommand(
        parsedCommand, gates, dev.cyr1en.promptcore.logic.transform.TemplateSyntax.DEFAULT);
  }

  public static ExecutionPlanDefinition fromParsedCommand(
      ParsedCommand parsedCommand,
      List<PreDispatchGateSpec> gates,
      dev.cyr1en.promptcore.logic.transform.TemplateSyntax syntax) {
    Objects.requireNonNull(parsedCommand, "parsedCommand must not be null");
    Objects.requireNonNull(gates, "gates must not be null");
    Objects.requireNonNull(syntax, "syntax must not be null");

    CompiledTemplate primaryCompiled =
        TemplateCompiler.compile(parsedCommand.templateCommand(), syntax);

    List<PostActionSpec> postActions = new ArrayList<>();
    for (PostCommandMeta pcm : parsedCommand.postCmds()) {
      postActions.add(toPostActionSpec(pcm, syntax));
    }

    return new ExecutionPlanDefinition(primaryCompiled, gates, postActions);
  }

  public static PostActionSpec toPostActionSpec(PostCommandMeta pcm) {
    return toPostActionSpec(pcm, dev.cyr1en.promptcore.logic.transform.TemplateSyntax.DEFAULT);
  }

  public static PostActionSpec toPostActionSpec(
      PostCommandMeta pcm, dev.cyr1en.promptcore.logic.transform.TemplateSyntax syntax) {
    Objects.requireNonNull(pcm, "pcm must not be null");
    Objects.requireNonNull(syntax, "syntax must not be null");

    ActionTrigger trigger = pcm.onCancel() ? ActionTrigger.ON_CANCEL : ActionTrigger.ON_SUCCESS;
    PostActionSpec baseSpec;

    if (pcm.isPreset()) {
      baseSpec =
          new PostActionSpec.PresetReference(
              pcm.command(), pcm.answerIndices(), trigger, pcm.dispatchTarget());
    } else {
      CompiledTemplate compiledTemplate = TemplateCompiler.compile(pcm.command(), syntax);
      int[] indices = pcm.answerIndices();
      if (indices.length == 0 && !compiledTemplate.referencedKeys().isEmpty()) {
        indices = PostActionSpec.extractAnswerIndices(compiledTemplate);
      }
      baseSpec =
          new PostActionSpec.ImmediateCommand(
              compiledTemplate, indices, trigger, pcm.dispatchTarget());
    }

    if (pcm.delayTicks() > 0) {
      return new PostActionSpec.Delayed(baseSpec, pcm.delayTicks(), trigger);
    }
    return baseSpec;
  }

  /**
   * Converts a {@link PostActionSpec} back to a legacy {@link PostCommandMeta}.
   *
   * @param spec the post-action spec to convert
   * @return the legacy post-command meta
   */
  public static PostCommandMeta toPostCommandMeta(PostActionSpec spec) {
    Objects.requireNonNull(spec, "spec must not be null");

    int delayTicks = 0;
    PostActionSpec unwrapped = spec;
    if (spec instanceof PostActionSpec.Delayed delayed) {
      delayTicks = delayed.delayTicks();
      unwrapped = delayed.delegate();
    }

    if (unwrapped instanceof PostActionSpec.ImmediateCommand immediate) {
      return new PostCommandMeta(
          immediate.commandTemplate().source(),
          immediate.answerIndices(),
          delayTicks,
          immediate.trigger().isOnCancel(),
          immediate.target(),
          false);
    }

    if (unwrapped instanceof PostActionSpec.PresetReference preset) {
      return new PostCommandMeta(
          preset.presetId(),
          preset.answerIndices(),
          delayTicks,
          preset.trigger().isOnCancel(),
          preset.target(),
          true);
    }

    throw new IllegalArgumentException(
        "Unknown PostActionSpec implementation: " + unwrapped.getClass().getName());
  }
}
