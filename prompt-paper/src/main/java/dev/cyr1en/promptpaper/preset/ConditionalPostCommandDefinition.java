package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import dev.cyr1en.promptcore.logic.condition.Condition;
import dev.cyr1en.promptcore.logic.condition.ConditionCompileOptions;
import dev.cyr1en.promptcore.logic.condition.ConditionCompiler;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Trusted preset conditional post-command definition.
 *
 * <p>Evaluates a compiled condition (with PlaceholderAPI support permitted) and executes either
 * the {@code if_true} or {@code if_false} trusted action branch.
 *
 * @param id the unique identifier matching {@code ^[a-z0-9_.-]{1,64}$}
 * @param condition the pre-compiled condition expression
 * @param executionPolicy when the conditional command is evaluated (on completion or cancellation)
 * @param ifTrueAction optional branch executed when the condition evaluates to true
 * @param ifFalseAction optional branch executed when the condition evaluates to false
 */
public record ConditionalPostCommandDefinition(
    String id,
    Condition condition,
    @SerializedName("execution_policy") ExecutionPolicy executionPolicy,
    @SerializedName("if_true") TrustedPresetAction ifTrueAction,
    @SerializedName("if_false") TrustedPresetAction ifFalseAction) {

  private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_.-]{1,64}$");

  public ConditionalPostCommandDefinition {
    validateId(id);
    Objects.requireNonNull(condition, "condition must not be null");
    Objects.requireNonNull(executionPolicy, "execution_policy must not be null");
    if (ifTrueAction == null && ifFalseAction == null) {
      throw new IllegalArgumentException(
          "At least one branch action (if_true or if_false) must be defined for conditional post-command '"
              + id
              + "'");
    }
  }

  public static void validateId(String id) {
    Objects.requireNonNull(id, "Conditional post-command id must not be null");
    if (!ID_PATTERN.matcher(id).matches()) {
      throw new IllegalArgumentException(
          "Conditional post-command id '" + id + "' does not match pattern ^[a-z0-9_.-]{1,64}$");
    }
  }

  public static ConditionalPostCommandDefinition compile(
      String id,
      String conditionSource,
      ExecutionPolicy executionPolicy,
      TrustedPresetAction ifTrueAction,
      TrustedPresetAction ifFalseAction) {
    Objects.requireNonNull(conditionSource, "conditionSource must not be null");
    Condition compiledCondition =
        ConditionCompiler.compile(conditionSource, ConditionCompileOptions.forPreset());
    return new ConditionalPostCommandDefinition(
        id, compiledCondition, executionPolicy, ifTrueAction, ifFalseAction);
  }
}
