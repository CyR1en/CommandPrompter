package dev.cyr1en.promptcore.plan;

import dev.cyr1en.promptcore.ParsedCommand;
import dev.cyr1en.promptcore.PostCommandMeta;
import dev.cyr1en.promptcore.logic.transform.CompiledTemplate;
import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import dev.cyr1en.promptcore.logic.transform.TemplateSyntax;
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
        parsedCommand, parsedCommand.preDispatchGates(), TemplateSyntax.DEFAULT);
  }

  public static ExecutionPlanDefinition fromParsedCommand(
      ParsedCommand parsedCommand, TemplateSyntax syntax) {
    Objects.requireNonNull(parsedCommand, "parsedCommand must not be null");
    return fromParsedCommand(parsedCommand, parsedCommand.preDispatchGates(), syntax);
  }

  public static ExecutionPlanDefinition fromParsedCommand(
      ParsedCommand parsedCommand, List<PreDispatchGateSpec> gates) {
    return fromParsedCommand(parsedCommand, gates, TemplateSyntax.DEFAULT);
  }

  public static ExecutionPlanDefinition fromParsedCommand(
      ParsedCommand parsedCommand, List<PreDispatchGateSpec> gates, TemplateSyntax syntax) {
    Objects.requireNonNull(parsedCommand, "parsedCommand must not be null");
    Objects.requireNonNull(gates, "gates must not be null");
    Objects.requireNonNull(syntax, "syntax must not be null");

    var primaryCompiled = TemplateCompiler.compile(parsedCommand.templateCommand(), syntax);
    var postActions =
        parsedCommand.postCmds().stream().map(pcm -> toPostActionSpec(pcm, syntax)).toList();

    return new ExecutionPlanDefinition(primaryCompiled, gates, postActions);
  }

  public static PostActionSpec toPostActionSpec(PostCommandMeta pcm) {
    return toPostActionSpec(pcm, TemplateSyntax.DEFAULT);
  }

  public static PostActionSpec toPostActionSpec(PostCommandMeta pcm, TemplateSyntax syntax) {
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
    return toPostCommandMeta(spec, 0);
  }

  private static PostCommandMeta toPostCommandMeta(PostActionSpec spec, int delayTicks) {
    return switch (spec) {
      case PostActionSpec.ImmediateCommand(var template, var indices, var trigger, var target) ->
          new PostCommandMeta(
              template.source(), indices, delayTicks, trigger.isOnCancel(), target, false);
      case PostActionSpec.PresetReference(var presetId, var indices, var trigger, var target) ->
          new PostCommandMeta(presetId, indices, delayTicks, trigger.isOnCancel(), target, true);
      case PostActionSpec.Delayed(var delegate, var ticks, _) -> toPostCommandMeta(delegate, ticks);
    };
  }
}
