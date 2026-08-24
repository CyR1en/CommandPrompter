package dev.cyr1en.promptpaper.execution.postaction.template;

import dev.cyr1en.promptcore.ParsedCommand;
import dev.cyr1en.promptcore.logic.transform.DefaultTransformer;
import dev.cyr1en.promptcore.logic.transform.MathMode;
import dev.cyr1en.promptcore.logic.transform.SingleTransformResult;
import dev.cyr1en.promptcore.logic.transform.TransformNotice;
import dev.cyr1en.promptpaper.execution.dispatch.ActionTrustLevel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable compiled action template consisting of typed segments.
 * <p>
 * Evaluates in a single pass during {@link #render(ActionTemplateBindings, MathMode, int)}.
 * All substituted values (player name, inputs, PAPI expansions) are treated as opaque literal data
 * and are never reparsed or re-expanded.
 */
public record CompiledActionTemplate(
        String source,
        ActionTrustLevel trustLevel,
        List<ActionTemplateSegment> segments
) {
    public CompiledActionTemplate {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(trustLevel, "trustLevel must not be null");
        Objects.requireNonNull(segments, "segments must not be null");
        segments = List.copyOf(segments);
    }

    /**
     * Returns the set of all core referenced keys in this template (e.g. "player", "0").
     *
     * @return unmodifiable set of referenced keys
     */
    public Set<String> referencedKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (ActionTemplateSegment segment : segments) {
            if (segment instanceof ActionTemplateSegment.Reference ref) {
                keys.add(ref.reference().key());
            }
        }
        return Collections.unmodifiableSet(keys);
    }

    /**
     * Returns the set of all PAPI tokens referenced in this template (if compiled with trust).
     *
     * @return unmodifiable set of PAPI tokens
     */
    public Set<String> papiTokens() {
        Set<String> tokens = new LinkedHashSet<>();
        for (ActionTemplateSegment segment : segments) {
            if (segment instanceof ActionTemplateSegment.Papi papi) {
                tokens.add(papi.token());
            }
        }
        return Collections.unmodifiableSet(tokens);
    }

    /**
     * Renders this compiled template using the provided bindings, default math mode, and max output length.
     *
     * @param bindings the runtime bindings
     * @return the rendered result
     */
    public ActionTemplateResult render(ActionTemplateBindings bindings) {
        return render(bindings, MathMode.LEGACY, ActionTemplateLimits.MAX_OUTPUT_LENGTH);
    }

    /**
     * Renders this compiled template using the provided bindings and math mode with max output length.
     *
     * @param bindings the runtime bindings
     * @param mathMode the math mode to apply to numeric transforms
     * @return the rendered result
     */
    public ActionTemplateResult render(ActionTemplateBindings bindings, MathMode mathMode) {
        return render(bindings, mathMode, ActionTemplateLimits.MAX_OUTPUT_LENGTH);
    }

    /**
     * Renders this compiled template using the provided bindings, math mode, and custom output length cap.
     *
     * @param bindings the runtime bindings
     * @param mathMode the math mode to apply to numeric transforms
     * @param maxOutputLength the maximum allowed character length of the rendered output
     * @return the rendered result
     */
    public ActionTemplateResult render(ActionTemplateBindings bindings, MathMode mathMode, int maxOutputLength) {
        Objects.requireNonNull(bindings, "bindings must not be null");
        if (mathMode == null) {
            mathMode = MathMode.LEGACY;
        }
        if (maxOutputLength <= 0 || maxOutputLength > ActionTemplateLimits.MAX_OUTPUT_LENGTH) {
            maxOutputLength = ActionTemplateLimits.MAX_OUTPUT_LENGTH;
        }

        StringBuilder sb = new StringBuilder();
        List<TransformNotice> notices = new ArrayList<>();

        for (ActionTemplateSegment segment : segments) {
            if (segment instanceof ActionTemplateSegment.Literal literal) {
                sb.append(literal.text());
                if (sb.length() > maxOutputLength) {
                    return ActionTemplateResult.failure(
                            ActionTemplateErrorCode.OUTPUT_TOO_LONG,
                            "Rendered output length exceeds limit of " + maxOutputLength + " characters"
                    );
                }
            } else if (segment instanceof ActionTemplateSegment.Reference ref) {
                var coreRef = ref.reference();
                String key = coreRef.key();
                String boundValue = bindings.get(key);

                if (boundValue != null) {
                    if (boundValue.length() > ActionTemplateLimits.MAX_INPUT_LENGTH) {
                        return ActionTemplateResult.failure(
                                ActionTemplateErrorCode.INPUT_TOO_LONG,
                                "Bound value for key '" + key + "' exceeds length limit of "
                                        + ActionTemplateLimits.MAX_INPUT_LENGTH
                        );
                    }
                    for (int i = 0; i < boundValue.length(); i++) {
                        char c = boundValue.charAt(i);
                        if (c < 0x20 || c == 0x7F) {
                            return ActionTemplateResult.failure(
                                    ActionTemplateErrorCode.CONTROL_CHARACTER_DETECTED,
                                    "Control character detected in bound value for key '" + key + "'"
                            );
                        }
                    }
                } else if (!(coreRef.transformer() instanceof DefaultTransformer)) {
                    return ActionTemplateResult.failure(
                            ActionTemplateErrorCode.MISSING_BINDING,
                            "Missing binding for reference key: " + key
                    );
                }

                SingleTransformResult transformResult = coreRef.transformer().transform(boundValue, mathMode);
                if (transformResult.isFailure()) {
                    return ActionTemplateResult.failure(ActionTemplateError.fromCore(transformResult.error()));
                }

                if (!transformResult.notices().isEmpty()) {
                    notices.addAll(transformResult.notices());
                }

                String transformedValue = transformResult.value();
                if (transformedValue != null) {
                    // Numeric keys are player answers. Keep each substitution as one command token;
                    // player/config metadata such as {player} remains ordinary template data.
                    sb.append(isAnswerKey(key)
                            ? ParsedCommand.formatCommandToken(transformedValue)
                            : transformedValue);
                    if (sb.length() > maxOutputLength) {
                        return ActionTemplateResult.failure(
                                ActionTemplateErrorCode.OUTPUT_TOO_LONG,
                                "Rendered output length exceeds limit of " + maxOutputLength + " characters"
                        );
                    }
                }
            } else if (segment instanceof ActionTemplateSegment.Papi papi) {
                String token = papi.token();
                PapiReferenceResolver resolver = bindings.papiResolver();
                if (resolver == null) {
                    return ActionTemplateResult.failure(
                            ActionTemplateErrorCode.PAPI_RESOLVER_MISSING,
                            "PAPI resolver is required to resolve '%" + token + "%'"
                    );
                }

                Optional<String> resolvedOpt;
                try {
                    resolvedOpt = resolver.resolve(token);
                } catch (Throwable t) {
                    return ActionTemplateResult.failure(
                            ActionTemplateErrorCode.PAPI_RESOLUTION_FAILED,
                            "PAPI resolution threw exception for '%" + token + "': " + t.getMessage(),
                            t
                    );
                }

                if (resolvedOpt == null || resolvedOpt.isEmpty()) {
                    return ActionTemplateResult.failure(
                            ActionTemplateErrorCode.PAPI_RESOLUTION_FAILED,
                            "Missing or empty PAPI resolution for '%" + token + "%'"
                    );
                }

                String resolved = resolvedOpt.get();
                if (resolved == null) {
                    return ActionTemplateResult.failure(
                            ActionTemplateErrorCode.PAPI_RESOLUTION_FAILED,
                            "PAPI resolution returned null for '%" + token + "%'"
                    );
                }

                if (resolved.length() > ActionTemplateLimits.MAX_INPUT_LENGTH) {
                    return ActionTemplateResult.failure(
                            ActionTemplateErrorCode.PAPI_VALUE_TOO_LONG,
                            "Resolved PAPI value for '%" + token + "%' exceeds length limit of "
                                    + ActionTemplateLimits.MAX_INPUT_LENGTH
                    );
                }

                for (int i = 0; i < resolved.length(); i++) {
                    char c = resolved.charAt(i);
                    if (c < 0x20 || c == 0x7F) {
                        return ActionTemplateResult.failure(
                                ActionTemplateErrorCode.PAPI_CONTROL_CHARACTER,
                                "Control character detected in resolved PAPI value for '%" + token + "%'"
                        );
                    }
                }

                // Trusted expansion output is still external data and must occupy one token.
                sb.append(ParsedCommand.formatCommandToken(resolved));
                if (sb.length() > maxOutputLength) {
                    return ActionTemplateResult.failure(
                            ActionTemplateErrorCode.OUTPUT_TOO_LONG,
                            "Rendered output length exceeds limit of " + maxOutputLength + " characters"
                    );
                }
            }
        }

        return ActionTemplateResult.success(sb.toString(), Collections.unmodifiableList(notices));
    }

    static boolean isAnswerKey(String key) {
        if (key == null || key.isEmpty()) return false;
        for (int i = 0; i < key.length(); i++) {
            if (!Character.isDigit(key.charAt(i))) return false;
        }
        return true;
    }
}
