package dev.cyr1en.promptpaper.execution.dispatch;

/**
 * Callback invoked upon completion of a primary command dispatch.
 *
 * <p>May report outcomes from any thread (e.g. console sender thread, global thread, or player
 * thread). Callers/coordinators are responsible for re-entering the initiator's PlayerExecutor
 * before performing entity-affine work.
 */
@FunctionalInterface
public interface PrimaryDispatchCallback {

    /**
     * Invoked when dispatch completes, either successfully or with a typed error.
     *
     * @param outcome the typed dispatch outcome
     */
    void onComplete(DispatchOutcome outcome);
}
