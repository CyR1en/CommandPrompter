package dev.cyr1en.promptpaper.engine;

/**
 * Explicit cancellation intent distinguishing user/runtime cancellations
 * (which run cancellation post-actions) from teardown/retirement cancellations
 * (which discard all state without creating executions or post-actions).
 */
public enum CancellationMode {
    /**
     * User-initiated or runtime cancellation where post-actions (e.g. on-cancel PCMs) should execute.
     * Used by manual cancel (/cmdp cancel), breakIf conditions, and error boundaries.
     */
    USER_ACTIONS,

    /**
     * Teardown cancellation where state is cleanly discarded without running post-actions or scheduling tasks.
     * Used by player quit/retirement, reload, plugin disable, and provider teardown.
     */
    DISCARD_ONLY
}
