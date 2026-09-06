package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import dev.cyr1en.promptcore.logic.transform.CompiledTemplate;
import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import dev.cyr1en.promptcore.logic.transform.TemplateSyntax;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Trusted preset approval gate definition.
 *
 * <p>Specifies approval requirements, target approver template, notification message template,
 * timeout bounds, self-approval policy, and optional immediate on-deny action.
 *
 * @param id the unique gate identifier matching {@code ^[a-z0-9_.-]{1,64}$}
 * @param target the compiled target approver template (permission or name expression)
 * @param message the compiled notification message template
 * @param timeout timeout in seconds (1..3600, default 30)
 * @param selfApprovalPolicy whether approvers auto-approve their own actions
 * @param onDenyAction optional immediate structured action executed upon denial (delay must be 0)
 */
public record ApprovalGateDefinition(
    String id,
    CompiledTemplate target,
    CompiledTemplate message,
    int timeout,
    @SerializedName("self_approval_policy") SelfApprovalPolicy selfApprovalPolicy,
    @SerializedName("on_deny") TrustedPresetAction onDenyAction) {

  public static final int DEFAULT_TIMEOUT = 30;
  public static final int MIN_TIMEOUT = 1;
  public static final int MAX_TIMEOUT = 3600;
  public static final int MAX_SOURCE_LENGTH = 1024;
  private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_.-]{1,64}$");
  private static final Pattern C0_CONTROLS = Pattern.compile("[\\u0000-\\u001F\\u007F]");

  public ApprovalGateDefinition {
    validateId(id);
    Objects.requireNonNull(target, "target template must not be null");
    Objects.requireNonNull(message, "message template must not be null");
    validateTemplate(target, "target");
    validateTemplate(message, "message");
    if (timeout < MIN_TIMEOUT || timeout > MAX_TIMEOUT) {
      throw new IllegalArgumentException(
          "timeout must be between " + MIN_TIMEOUT + " and " + MAX_TIMEOUT + ", got: " + timeout);
    }
    selfApprovalPolicy =
        selfApprovalPolicy == null ? SelfApprovalPolicy.AUTO_APPROVE : selfApprovalPolicy;
    if (onDenyAction != null) {
      if (onDenyAction.delayTicks() != 0) {
        throw new IllegalArgumentException(
            "on_deny action delay_ticks must be 0 in this phase, got: "
                + onDenyAction.delayTicks());
      }
    }
  }

  public ApprovalGateDefinition(String id, CompiledTemplate target, CompiledTemplate message) {
    this(id, target, message, DEFAULT_TIMEOUT, SelfApprovalPolicy.AUTO_APPROVE, null);
  }

  public ApprovalGateDefinition(
      String id, CompiledTemplate target, CompiledTemplate message, int timeout) {
    this(id, target, message, timeout, SelfApprovalPolicy.AUTO_APPROVE, null);
  }

  public static void validateId(String id) {
    Objects.requireNonNull(id, "Approval gate id must not be null");
    if (!ID_PATTERN.matcher(id).matches()) {
      throw new IllegalArgumentException(
          "Approval gate id '" + id + "' does not match pattern ^[a-z0-9_.-]{1,64}$");
    }
  }

  public static ApprovalGateDefinition of(
      String id,
      String targetSource,
      String messageSource,
      int timeout,
      SelfApprovalPolicy selfApprovalPolicy,
      TrustedPresetAction onDenyAction) {
    return of(
        id,
        targetSource,
        messageSource,
        timeout,
        selfApprovalPolicy,
        onDenyAction,
        TemplateSyntax.DEFAULT);
  }

  public static ApprovalGateDefinition of(
      String id,
      String targetSource,
      String messageSource,
      int timeout,
      SelfApprovalPolicy selfApprovalPolicy,
      TrustedPresetAction onDenyAction,
      TemplateSyntax syntax) {
    validateId(id);
    Objects.requireNonNull(targetSource, "targetSource must not be null");
    Objects.requireNonNull(messageSource, "messageSource must not be null");
    Objects.requireNonNull(syntax, "syntax must not be null");
    validateSource(targetSource, "target");
    validateSource(messageSource, "message");
    return new ApprovalGateDefinition(
        id,
        TemplateCompiler.compile(targetSource, syntax),
        TemplateCompiler.compile(messageSource, syntax),
        timeout,
        selfApprovalPolicy,
        onDenyAction);
  }

  private static void validateSource(String source, String fieldName) {
    if (source.isBlank()) {
      throw new IllegalArgumentException(fieldName + " template source must not be empty or blank");
    }
    if (source.length() > MAX_SOURCE_LENGTH) {
      throw new IllegalArgumentException(
          fieldName
              + " template source length ("
              + source.length()
              + ") exceeds maximum limit of "
              + MAX_SOURCE_LENGTH);
    }
    if (C0_CONTROLS.matcher(source).find()) {
      throw new IllegalArgumentException(
          fieldName + " template source contains forbidden raw C0 control characters");
    }
  }

  private static void validateTemplate(CompiledTemplate template, String fieldName) {
    if (template.source().isBlank()) {
      throw new IllegalArgumentException(fieldName + " template must not be empty or blank");
    }
    if (template.source().length() > MAX_SOURCE_LENGTH) {
      throw new IllegalArgumentException(
          fieldName
              + " template source length ("
              + template.source().length()
              + ") exceeds maximum limit of "
              + MAX_SOURCE_LENGTH);
    }
    if (C0_CONTROLS.matcher(template.source()).find()) {
      throw new IllegalArgumentException(
          fieldName + " template source contains forbidden raw C0 control characters");
    }
  }
}
