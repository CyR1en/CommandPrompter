package dev.cyr1en.promptcore.plan;

import dev.cyr1en.promptcore.ParsedCommand;
import dev.cyr1en.promptcore.PostCommandMeta;
import dev.cyr1en.promptcore.logic.transform.CompiledTemplate;
import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import dev.cyr1en.promptcore.logic.transform.TemplateSyntax;
import dev.cyr1en.promptcore.logic.transform.TransformErrorCode;
import dev.cyr1en.promptcore.logic.transform.TransformException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

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

    var primaryCompiled = TemplateCompiler.compile(parsedCommand.primaryTemplate(), syntax);
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
      var compiledTemplate = compilePostAction(pcm.command(), syntax);
      var indices =
          IntStream.concat(
                  Arrays.stream(pcm.answerIndices()),
                  Arrays.stream(PostActionSpec.extractAnswerIndices(compiledTemplate)))
              .toArray();
      baseSpec =
          new PostActionSpec.ImmediateCommand(
              compiledTemplate, indices, trigger, pcm.dispatchTarget());
    }

    if (pcm.delayTicks() > 0) {
      return new PostActionSpec.Delayed(baseSpec, pcm.delayTicks(), trigger);
    }
    return baseSpec;
  }

  private static CompiledTemplate compilePostAction(String command, TemplateSyntax syntax) {
    // Match Paper's ActionTemplateCompiler for legacy inputs and escaped percent signs.
    var separator = syntax.transformSeparator();
    var inputPattern =
        Pattern.compile(
            "^input(?::(\\d+))?(?:" + Pattern.quote(separator) + "(.*))?$", Pattern.DOTALL);
    return TemplateCompiler.compile(
        command,
        syntax,
        (body, offset) -> {
          var trimmed = body.strip();
          var input = inputPattern.matcher(trimmed);
          if (!input.matches()) return trimmed;

          var digits = input.group(1);
          int index = 1;
          if (digits != null) {
            try {
              index = Integer.parseInt(digits);
            } catch (NumberFormatException e) {
              throw new TransformException(
                  TransformErrorCode.MALFORMED_PLACEHOLDER,
                  "Input placeholder index is invalid: " + digits + " at index " + offset);
            }
            if (index < 1) {
              throw new TransformException(
                  TransformErrorCode.MALFORMED_PLACEHOLDER,
                  "Input placeholder index must be positive (>= 1), got: "
                      + index
                      + " at index "
                      + offset);
            }
          }
          var transformer = input.group(2);
          var key = String.valueOf(index - 1);
          return transformer == null || transformer.isBlank() ? key : key + separator + transformer;
        },
        Set.of("%"));
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
