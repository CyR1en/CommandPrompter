package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import dev.cyr1en.promptcore.logic.transform.CompiledTemplate;
import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import dev.cyr1en.promptcore.logic.transform.TemplateSyntax;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable trusted action definition containing a pre-compiled command template,
 * execution target context, and scheduled tick delay.
 *
 * <p>Constructed only from trusted administrative sources such as {@code presets.json}.
 *
 * @param command the pre-compiled command template
 * @param executeAs whether the command is dispatched as player or console
 * @param delayTicks the dispatch delay in server ticks (0..72000)
 */
public record TrustedPresetAction(
    CompiledTemplate command,
    @SerializedName("execute_as") ExecuteAs executeAs,
    @SerializedName("delay_ticks") int delayTicks) {

  public static final int MAX_COMMAND_LENGTH = 1024;
  public static final int MAX_DELAY_TICKS = 72000;
  private static final Pattern C0_CONTROLS = Pattern.compile("[\\u0000-\\u001F\\u007F]");

  public TrustedPresetAction {
    Objects.requireNonNull(command, "command template must not be null");
    Objects.requireNonNull(executeAs, "execute_as must not be null");
    if (command.source().length() > MAX_COMMAND_LENGTH) {
      throw new IllegalArgumentException(
          "Action command length ("
              + command.source().length()
              + ") exceeds maximum limit of "
              + MAX_COMMAND_LENGTH);
    }
    if (C0_CONTROLS.matcher(command.source()).find()) {
      throw new IllegalArgumentException(
          "Action command contains forbidden raw C0 control characters");
    }
    if (delayTicks < 0 || delayTicks > MAX_DELAY_TICKS) {
      throw new IllegalArgumentException(
          "delay_ticks must be between 0 and " + MAX_DELAY_TICKS + ", got: " + delayTicks);
    }
  }

  public static TrustedPresetAction of(CompiledTemplate command, ExecuteAs executeAs, int delayTicks) {
    return new TrustedPresetAction(command, executeAs, delayTicks);
  }

  public static TrustedPresetAction of(CompiledTemplate command, ExecuteAs executeAs) {
    return new TrustedPresetAction(command, executeAs, 0);
  }

  public static TrustedPresetAction of(String commandSource, ExecuteAs executeAs, int delayTicks) {
    return of(commandSource, executeAs, delayTicks, TemplateSyntax.DEFAULT);
  }

  public static TrustedPresetAction of(String commandSource, ExecuteAs executeAs, int delayTicks, TemplateSyntax syntax) {
    Objects.requireNonNull(commandSource, "command source must not be null");
    Objects.requireNonNull(syntax, "syntax must not be null");
    if (commandSource.isBlank()) {
      throw new IllegalArgumentException("Action command cannot be empty or blank");
    }
    if (commandSource.length() > MAX_COMMAND_LENGTH) {
      throw new IllegalArgumentException(
          "Action command length ("
              + commandSource.length()
              + ") exceeds maximum limit of "
              + MAX_COMMAND_LENGTH);
    }
    if (C0_CONTROLS.matcher(commandSource).find()) {
      throw new IllegalArgumentException(
          "Action command contains forbidden raw C0 control characters");
    }
    CompiledTemplate compiled = TemplateCompiler.compile(commandSource, syntax);
    return new TrustedPresetAction(compiled, executeAs, delayTicks);
  }

  public static TrustedPresetAction of(String commandSource, ExecuteAs executeAs) {
    return of(commandSource, executeAs, 0, TemplateSyntax.DEFAULT);
  }

  public static TrustedPresetAction of(String commandSource, ExecuteAs executeAs, TemplateSyntax syntax) {
    return of(commandSource, executeAs, 0, syntax);
  }
}
