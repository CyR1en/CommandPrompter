package dev.cyr1en.promptpaper.screen;

import dev.cyr1en.promptpaper.preset.ActionsSource;
import dev.cyr1en.promptpaper.preset.DialogType;
import dev.cyr1en.promptpaper.preset.DialogTypeConfig;
import java.util.List;
import java.util.function.Supplier;

/**
 * Immutable snapshot of the concrete tab-completion flow for one dialog open.
 *
 * <p>Resolved exactly once at {@link DialogPromptScreen#open()} time from a
 * single completion lookup via {@link #resolveOnce}. Every downstream decision
 * for the new-model (JSON preset) path — the fallback body notice, the
 * injected text input, the dialog type/actions, and the effective answer
 * arity — reads this snapshot instead of re-consulting the completion service,
 * so a completion service that changes its answers between calls can never
 * desync the rendered inputs from the encoded result arity.
 *
 * <p>Package-private and Paper-free so the once-per-open contract can be unit
 * tested without loading the Paper-bound {@code DialogPromptScreen}.
 */
record TabFlowState(List<String> completions, boolean fallback) {

    TabFlowState {
        completions = List.copyOf(completions);
    }

    /**
     * Resolves the concrete flow for a dialog-type config, invoking the
     * completion lookup at most once. Only a {@code multi_action} dialog with
     * a {@code tab_completion} source has a tab flow; every other layout gets
     * an empty, non-fallback snapshot and never calls the supplier.
     *
     * @param dialogType the preset dialog-type block
     * @param maxButtons the configured {@code MaxButtons} threshold; a
     *     completion count of zero or greater than it falls back to a text input
     * @param completions the single completion lookup for this open; invoked
     *     only for tab-completion multi-action dialogs
     */
    static TabFlowState resolveOnce(
            DialogTypeConfig dialogType, int maxButtons, Supplier<List<String>> completions) {
        if (dialogType.type() != DialogType.MULTI_ACTION
                || dialogType.actionsSource() != ActionsSource.TAB_COMPLETION) {
            return new TabFlowState(List.of(), false);
        }
        var snapshot = completions.get();
        var frozen = List.copyOf(snapshot);
        return new TabFlowState(frozen, frozen.isEmpty() || frozen.size() > maxButtons);
    }

    /**
     * Effective answer arity for a multi-action tab-completion flow built from
     * this snapshot: the injected fallback input adds one answer when the flow
     * fell back to a confirmation layout.
     */
    int multiActionArity(int configuredInputRows) {
        return fallback ? configuredInputRows + 1 : 1;
    }
}
