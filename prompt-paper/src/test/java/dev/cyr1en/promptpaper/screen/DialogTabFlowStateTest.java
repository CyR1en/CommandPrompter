package dev.cyr1en.promptpaper.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cyr1en.promptpaper.preset.ActionsSource;
import dev.cyr1en.promptpaper.preset.DialogType;
import dev.cyr1en.promptpaper.preset.DialogTypeConfig;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Issue #92 reconciliation: the concrete tab-completion flow must be decided
 * exactly once per open. {@link TabFlowState#resolveOnce} takes the single
 * completion snapshot and every downstream decision (fallback notice, injected
 * input, dialog type/actions, effective answer arity) derives from it — so a
 * completion service returning different values on successive calls can never
 * desync the rendered inputs from the encoded result arity.
 *
 * <p>Kept Paper-free on purpose: the snapshot helper is extracted so this
 * contract is testable without loading the Paper-bound
 * {@code DialogPromptScreen} (whose {@code open()} reaches into the native
 * dialog UI).
 */
class DialogTabFlowStateTest {

    private static final int MAX_BUTTONS = 5;

    private static DialogTypeConfig multiActionTab() {
        return new DialogTypeConfig(
                DialogType.MULTI_ACTION, 1, List.of(), ActionsSource.TAB_COMPLETION, null, null, null);
    }

    private static DialogTypeConfig multiActionStatic() {
        return new DialogTypeConfig(DialogType.MULTI_ACTION, 1, List.of(), null, null, null, null);
    }

    private static DialogTypeConfig confirmation() {
        return new DialogTypeConfig(DialogType.CONFIRMATION, null, List.of(), null, null, null, null);
    }

    /** Returns different values on successive calls: a changing completion service. */
    private static Supplier<List<String>> changingSupplier(AtomicInteger calls) {
        return () -> {
            int call = calls.incrementAndGet();
            // First call: 2 completions (fits the grid). Any later call would
            // return empty and flip the flow to fallback if it were consulted.
            return call == 1 ? List.of("alpha", "beta") : List.of();
        };
    }

    @Test
    void completionLookupRunsExactlyOnceAndSnapshotIsFrozen() {
        var calls = new AtomicInteger();
        var state = TabFlowState.resolveOnce(multiActionTab(), MAX_BUTTONS, changingSupplier(calls));

        assertEquals(1, calls.get(), "completion lookup must run exactly once per open");
        assertFalse(state.fallback(), "2 completions within the MaxButtons threshold keep the grid");
        assertEquals(List.of("alpha", "beta"), state.completions(),
                "downstream must see the single open-time snapshot, not a later lookup");
        // A second lookup would have returned empty and flipped the flow to
        // fallback — the frozen snapshot must prevent that from ever happening.
        assertEquals(1, state.multiActionArity(2), "non-fallback multi-action arity is exactly one");
    }

    @Test
    void fallbackSnapshotDrivesArityFromTheSingleLookup() {
        var calls = new AtomicInteger();
        var state = TabFlowState.resolveOnce(multiActionTab(), MAX_BUTTONS, () -> {
            calls.incrementAndGet();
            return List.of("a", "b", "c", "d", "e", "f"); // 6 > 5 → fallback
        });

        assertEquals(1, calls.get());
        assertTrue(state.fallback());
        assertEquals(6, state.completions().size());
        assertEquals(3, state.multiActionArity(2),
                "fallback flow adds one injected answer to the configured inputs");
    }

    @Test
    void emptyCompletionSnapshotFallsBack() {
        var state = TabFlowState.resolveOnce(multiActionTab(), MAX_BUTTONS, List::of);

        assertTrue(state.fallback());
        assertTrue(state.completions().isEmpty());
        assertEquals(1, state.multiActionArity(0), "zero configured inputs + injected input");
    }

    @Test
    void exactlyMaxButtonsCompletionsKeepTheButtonGrid() {
        var state = TabFlowState.resolveOnce(multiActionTab(), MAX_BUTTONS,
                () -> List.of("a", "b", "c", "d", "e"));

        assertFalse(state.fallback(), "the MaxButtons threshold is inclusive");
        assertEquals(5, state.completions().size());
    }

    @Test
    void nonTabLayoutsNeverInvokeCompletionLookup() {
        var calls = new AtomicInteger();
        Supplier<List<String>> exploding = () -> {
            calls.incrementAndGet();
            throw new AssertionError("completion lookup must not run for non-tab layouts");
        };

        var staticFlow = TabFlowState.resolveOnce(multiActionStatic(), MAX_BUTTONS, exploding);
        assertFalse(staticFlow.fallback());
        assertTrue(staticFlow.completions().isEmpty());
        assertEquals(1, staticFlow.multiActionArity(3));

        var confirmFlow = TabFlowState.resolveOnce(confirmation(), MAX_BUTTONS, exploding);
        assertFalse(confirmFlow.fallback());
        assertTrue(confirmFlow.completions().isEmpty());

        assertEquals(0, calls.get(), "non-tab layouts must not touch the completion service");
    }
}
