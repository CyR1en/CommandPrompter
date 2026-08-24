package dev.cyr1en.promptpaper.execution.dispatch;

/**
 * Execution mode for primary command dispatch.
 */
public enum DispatchMode {
    /** Dispatches as the player on the player's entity thread. */
    PLAYER,
    /** Dispatches as the server console on the global region thread. */
    CONSOLE,
    /** Dispatches as the player with temporary permission attachment. */
    ATTACHMENT
}
