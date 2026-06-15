package dev.cyr1en.promptui;

import java.util.function.Consumer;

/**
 * Generic lifecycle for an interactive prompt screen.
 *
 * <p>Implementations are platform-bound: Anvil/Sign screens are NMS-backed and run on the
 * Bukkit main thread (or the player scheduler's own thread). All callback invocations are
 * guaranteed to occur on that same thread; consumers should not assume the call originates
 * from any particular thread context outside of that guarantee.
 *
 * <p>The lifecycle is:
 * <ol>
 *   <li>{@link #configure(java.util.Map)} (optional, only for anvil/sign subtypes)</li>
 *   <li>{@link #onResult(Consumer)} (registers a single callback)</li>
 *   <li>{@link #open()}</li>
 *   <li>either {@link #close()} or a single {@code ScreenResult} delivered to the callback</li>
 * </ol>
 *
 * <p>Implementations are not required to be thread-safe. Callers should invoke lifecycle
 * methods from the main thread (or via the player scheduler).
 */
public interface InputScreen {

    /** Opens the screen for the player. */
    void open();

    /** Closes the screen without delivering a result. */
    void close();

    /** Returns whether this screen is currently open. */
    boolean isOpen();

    /**
     * Registers a callback to receive the screen result when the player submits or cancels.
     *
     * @param callback invoked once with the result, then discarded
     */
    void onResult(Consumer<ScreenResult> callback);
}
