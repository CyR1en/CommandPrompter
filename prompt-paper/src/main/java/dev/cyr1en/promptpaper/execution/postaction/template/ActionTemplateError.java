package dev.cyr1en.promptpaper.execution.postaction.template;

import dev.cyr1en.promptcore.logic.transform.TransformError;
import dev.cyr1en.promptcore.logic.transform.TransformErrorCode;
import java.util.Objects;

/**
 * Immutable failure descriptor for action template compilation or rendering.
 */
public record ActionTemplateError(
        ActionTemplateErrorCode code,
        String message,
        Throwable cause
) {
    public ActionTemplateError {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public static ActionTemplateError of(ActionTemplateErrorCode code, String message) {
        return new ActionTemplateError(code, message, null);
    }

    public static ActionTemplateError of(ActionTemplateErrorCode code, String message, Throwable cause) {
        return new ActionTemplateError(code, message, cause);
    }

    public static ActionTemplateError fromCore(TransformError coreError) {
        Objects.requireNonNull(coreError, "coreError must not be null");
        ActionTemplateErrorCode code = fromCoreCode(coreError.code());
        return new ActionTemplateError(code, coreError.message(), null);
    }

    public static ActionTemplateErrorCode fromCoreCode(TransformErrorCode code) {
        if (code == null) {
            return ActionTemplateErrorCode.MALFORMED_TEMPLATE;
        }
        return switch (code) {
            case TEMPLATE_TOO_LONG -> ActionTemplateErrorCode.SOURCE_TOO_LONG;
            case MALFORMED_TEMPLATE -> ActionTemplateErrorCode.MALFORMED_TEMPLATE;
            case MALFORMED_ESCAPE -> ActionTemplateErrorCode.MALFORMED_ESCAPE;
            case MALFORMED_PLACEHOLDER -> ActionTemplateErrorCode.MALFORMED_PLACEHOLDER;
            case UNKNOWN_TRANSFORMER -> ActionTemplateErrorCode.UNKNOWN_TRANSFORMER;
            case CHAINED_TRANSFORMER -> ActionTemplateErrorCode.CHAINED_TRANSFORMER;
            case MALFORMED_TRANSFORMER_ARGUMENT -> ActionTemplateErrorCode.MALFORMED_TRANSFORMER_ARGUMENT;
            case INPUT_TOO_LONG -> ActionTemplateErrorCode.INPUT_TOO_LONG;
            case OUTPUT_TOO_LONG -> ActionTemplateErrorCode.OUTPUT_TOO_LONG;
            case CONTROL_CHARACTER_DETECTED -> ActionTemplateErrorCode.CONTROL_CHARACTER_DETECTED;
            case NON_NUMERIC_INPUT -> ActionTemplateErrorCode.NON_NUMERIC_INPUT;
            case MATH_EXPRESSION_TOO_LONG -> ActionTemplateErrorCode.MATH_EXPRESSION_TOO_LONG;
            case TOO_MANY_OPERATIONS -> ActionTemplateErrorCode.TOO_MANY_OPERATIONS;
            case MATH_SYNTAX_ERROR -> ActionTemplateErrorCode.MATH_SYNTAX_ERROR;
            case MAGNITUDE_EXCEEDED -> ActionTemplateErrorCode.MAGNITUDE_EXCEEDED;
            case DIVISION_BY_ZERO -> ActionTemplateErrorCode.DIVISION_BY_ZERO;
            case MISSING_BINDING -> ActionTemplateErrorCode.MISSING_BINDING;
        };
    }
}
