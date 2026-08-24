package dev.cyr1en.promptpaper.execution.postaction.template;

import dev.cyr1en.promptcore.logic.transform.TransformNotice;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable outcome of rendering an action template.
 */
public record ActionTemplateResult(
        boolean isSuccess,
        String renderedText,
        List<TransformNotice> notices,
        ActionTemplateError error
) {
    public ActionTemplateResult {
        notices = notices == null ? List.of() : List.copyOf(notices);
    }

    public static ActionTemplateResult success(String renderedText, List<TransformNotice> notices) {
        Objects.requireNonNull(renderedText, "renderedText must not be null");
        return new ActionTemplateResult(true, renderedText, notices, null);
    }

    public static ActionTemplateResult success(String renderedText) {
        return success(renderedText, List.of());
    }

    public static ActionTemplateResult failure(ActionTemplateError error) {
        Objects.requireNonNull(error, "error must not be null");
        return new ActionTemplateResult(false, null, List.of(), error);
    }

    public static ActionTemplateResult failure(ActionTemplateErrorCode code, String message) {
        return failure(ActionTemplateError.of(code, message));
    }

    public static ActionTemplateResult failure(ActionTemplateErrorCode code, String message, Throwable cause) {
        return failure(ActionTemplateError.of(code, message, cause));
    }

    public boolean isFailure() {
        return !isSuccess;
    }

    public Optional<ActionTemplateError> optionalError() {
        return Optional.ofNullable(error);
    }

    public Optional<String> optionalRenderedText() {
        return Optional.ofNullable(renderedText);
    }
}
