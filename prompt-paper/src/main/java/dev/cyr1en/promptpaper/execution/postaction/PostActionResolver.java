package dev.cyr1en.promptpaper.execution.postaction;

import dev.cyr1en.promptcore.DispatchTarget;
import dev.cyr1en.promptcore.PostCommandMeta;
import dev.cyr1en.promptcore.logic.condition.ConditionBindings;
import dev.cyr1en.promptcore.logic.condition.ConditionEvaluationException;
import dev.cyr1en.promptcore.logic.transform.TemplateSyntax;
import dev.cyr1en.promptpaper.execution.dispatch.ActionProvenance;
import dev.cyr1en.promptpaper.execution.dispatch.ActionTrustLevel;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchErrorKind;
import dev.cyr1en.promptpaper.execution.postaction.template.ActionTemplateCompiler;
import dev.cyr1en.promptpaper.execution.postaction.template.ActionTemplateException;
import dev.cyr1en.promptpaper.execution.postaction.template.ActionTemplateLimits;
import dev.cyr1en.promptpaper.execution.postaction.template.CompiledActionTemplate;
import dev.cyr1en.promptpaper.execution.postaction.template.PapiReferenceResolver;
import dev.cyr1en.promptpaper.execution.runtime.InputCompletion;
import dev.cyr1en.promptpaper.preset.ConditionalPostCommandDefinition;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.ExecutionPolicy;
import dev.cyr1en.promptpaper.preset.PostCommand;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import dev.cyr1en.promptpaper.preset.TrustedPresetAction;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure resolver translating raw {@link PostCommandMeta} definitions into fully prepared
 * {@link ResolvedPostAction} instances ready for execution.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Authoritative execution policy filtering (presets override parser hints; inline uses onCancel marker).
 *   <li>Captured snapshot resolution without live singleton lookup.
 *   <li>Fail-closed error semantics on unknown preset IDs, compile errors, or condition evaluation failures.
 *   <li>Untrusted inline execution templates without PAPI parsing.
 *   <li>Strict condition evaluation with per-reference PAPI validation (max 1024, C0-safe, missing fail closed).
 * </ul>
 */
public final class PostActionResolver {

    private PostActionResolver() {}

    /**
     * Resolves a single {@link PostCommandMeta} into a {@link ResolvedPostAction} using default template syntax.
     *
     * @param pcm the post-command metadata
     * @param lifecyclePolicy active lifecycle policy (ON_COMPLETE or ON_CANCEL)
     * @param completion immutable input completion containing snapshot and answers
     * @param papiResolver PlaceholderAPI reference resolver
     * @return the resolved action, or {@code null} if skipped due to lifecycle policy mismatch
     * @throws PostActionResolutionException if resolution fails closed (unknown preset, compile error, condition failure)
     */
    public static ResolvedPostAction resolve(
            PostCommandMeta pcm,
            ExecutionPolicy lifecyclePolicy,
            InputCompletion completion,
            PapiReferenceResolver papiResolver
    ) {
        return resolve(pcm, lifecyclePolicy, completion, papiResolver, TemplateSyntax.DEFAULT);
    }

    /**
     * Resolves a single {@link PostCommandMeta} into a {@link ResolvedPostAction} using the specified template syntax.
     *
     * @param pcm the post-command metadata
     * @param lifecyclePolicy active lifecycle policy (ON_COMPLETE or ON_CANCEL)
     * @param completion immutable input completion containing snapshot and answers
     * @param papiResolver PlaceholderAPI reference resolver
     * @param syntax template syntax delimiters
     * @return the resolved action, or {@code null} if skipped due to lifecycle policy mismatch
     * @throws PostActionResolutionException if resolution fails closed (unknown preset, compile error, condition failure)
     */
    public static ResolvedPostAction resolve(
            PostCommandMeta pcm,
            ExecutionPolicy lifecyclePolicy,
            InputCompletion completion,
            PapiReferenceResolver papiResolver,
            TemplateSyntax syntax
    ) {
        if (pcm == null) {
            return null;
        }
        Objects.requireNonNull(lifecyclePolicy, "lifecyclePolicy must not be null");
        Objects.requireNonNull(completion, "completion must not be null");
        Objects.requireNonNull(syntax, "syntax must not be null");

        if (pcm.isPreset()) {
            return resolvePreset(pcm, lifecyclePolicy, completion, papiResolver, syntax);
        } else {
            return resolveLegacyInline(pcm, lifecyclePolicy, syntax);
        }
    }

    private static ResolvedPostAction resolveLegacyInline(
            PostCommandMeta pcm,
            ExecutionPolicy lifecyclePolicy,
            TemplateSyntax syntax
    ) {
        boolean onCancelPolicy = (lifecyclePolicy == ExecutionPolicy.ON_CANCEL);
        if (pcm.onCancel() != onCancelPolicy) {
            return null;
        }

        String rawCommand = pcm.command();
        CompiledActionTemplate template;
        try {
            template = ActionTemplateCompiler.compile(rawCommand, syntax, ActionTrustLevel.UNTRUSTED_INLINE);
        } catch (ActionTemplateException e) {
            throw new PostActionResolutionException(
                    DispatchErrorKind.INVALID_REQUEST,
                    "Failed to compile inline post-action template: " + e.getMessage(),
                    e
            );
        }

        ExecuteAs executeAs = (pcm.dispatchTarget() == DispatchTarget.CONSOLE)
                ? ExecuteAs.CONSOLE
                : ExecuteAs.PLAYER;

        return ResolvedPostAction.of(
                template,
                executeAs,
                pcm.delayTicks(),
                ActionProvenance.untrustedInline(),
                null
        );
    }

    private static ResolvedPostAction resolvePreset(
            PostCommandMeta pcm,
            ExecutionPolicy lifecyclePolicy,
            InputCompletion completion,
            PapiReferenceResolver papiResolver,
            TemplateSyntax syntax
    ) {
        String presetId = pcm.command();
        PresetSnapshot snapshot = completion.capturedPresetSnapshot();
        if (snapshot == null) {
            throw new PostActionResolutionException(
                    DispatchErrorKind.INVALID_REQUEST,
                    "No captured PresetSnapshot available to resolve preset ID: " + presetId
            );
        }

        Optional<PostCommand> legacyPostCommand = snapshot.getPostCommand(presetId);
        Optional<ConditionalPostCommandDefinition> conditionalPostCommand = snapshot.getConditionalPostCommand(presetId);

        if (legacyPostCommand.isEmpty() && conditionalPostCommand.isEmpty()) {
            throw new PostActionResolutionException(
                    DispatchErrorKind.INVALID_REQUEST,
                    "Unknown preset post-command ID in captured snapshot: " + presetId
            );
        }

        if (legacyPostCommand.isPresent()) {
            PostCommand def = legacyPostCommand.get();
            if (def.executionPolicy() != lifecyclePolicy) {
                return null;
            }

            CompiledActionTemplate template;
            try {
                template = ActionTemplateCompiler.compile(def.command(), syntax, ActionTrustLevel.TRUSTED_PRESET);
            } catch (ActionTemplateException e) {
                throw new PostActionResolutionException(
                        DispatchErrorKind.INVALID_REQUEST,
                        "Failed to compile preset post-command template '" + presetId + "': " + e.getMessage(),
                        e
                );
            }

            return ResolvedPostAction.of(
                    template,
                    def.executeAs(),
                    def.delayTicks(),
                    ActionProvenance.trustedPreset(def.id(), def.executeAs() == ExecuteAs.CONSOLE),
                    def.id()
            );
        }

        ConditionalPostCommandDefinition def = conditionalPostCommand.get();
        if (def.executionPolicy() != lifecyclePolicy) {
            return null;
        }

        List<String> answers = completion.answers();
        ConditionBindings conditionBindings = new ConditionBindings() {
            @Override
            public Optional<String> getAnswer(int index) {
                if (index >= 0 && index < answers.size()) {
                    return Optional.ofNullable(answers.get(index));
                }
                return Optional.empty();
            }

            @Override
            public Optional<String> getPlaceholder(String placeholder) {
                if (papiResolver == null || placeholder == null) {
                    return Optional.empty();
                }
                Optional<String> resolvedOpt;
                try {
                    resolvedOpt = papiResolver.resolve(placeholder);
                } catch (Throwable t) {
                    throw new ConditionEvaluationException(
                            "PlaceholderAPI resolution failed for %" + placeholder + "%: " + t.getMessage(), t);
                }
                if (resolvedOpt == null || resolvedOpt.isEmpty()) {
                    return Optional.empty();
                }
                String val = resolvedOpt.get();
                if (val == null) {
                    return Optional.empty();
                }
                if (val.length() > ActionTemplateLimits.MAX_INPUT_LENGTH) {
                    throw new ConditionEvaluationException(
                            "Resolved placeholder %" + placeholder + "% length (" + val.length()
                                    + ") exceeds maximum limit of " + ActionTemplateLimits.MAX_INPUT_LENGTH);
                }
                for (int i = 0; i < val.length(); i++) {
                    char c = val.charAt(i);
                    if (c < 0x20 || c == 0x7F) {
                        throw new ConditionEvaluationException(
                                "Control character detected in resolved placeholder %" + placeholder + "%");
                    }
                }
                return Optional.of(val);
            }
        };

        boolean conditionResult;
        try {
            conditionResult = def.condition().evaluate(conditionBindings);
        } catch (ConditionEvaluationException e) {
            throw new PostActionResolutionException(
                    DispatchErrorKind.INVALID_REQUEST,
                    "Condition evaluation failed for preset '" + presetId + "': " + e.getMessage(),
                    e
            );
        }

        TrustedPresetAction branch = conditionResult ? def.ifTrueAction() : def.ifFalseAction();
        if (branch == null) {
            return ResolvedPostAction.noOp();
        }

        CompiledActionTemplate template;
        try {
            template = ActionTemplateCompiler.compile(branch.command().source(), syntax, ActionTrustLevel.TRUSTED_PRESET);
        } catch (ActionTemplateException e) {
            throw new PostActionResolutionException(
                    DispatchErrorKind.INVALID_REQUEST,
                    "Failed to compile conditional branch template for preset '" + presetId + "': " + e.getMessage(),
                    e
            );
        }

        return ResolvedPostAction.of(
                template,
                branch.executeAs(),
                branch.delayTicks(),
                ActionProvenance.trustedPreset(def.id(), branch.executeAs() == ExecuteAs.CONSOLE),
                def.id()
        );
    }
}
