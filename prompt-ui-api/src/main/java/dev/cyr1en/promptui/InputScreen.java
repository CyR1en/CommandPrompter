package dev.cyr1en.promptui;

import java.util.function.Consumer;

/**
 * Generic lifecycle for an interactive prompt screen.
 *
 * <p>The lifecycle is:
 *
 * <ol>
 *   <li>{@link #onResult(Consumer)} (registers the completion callback)
 *   <li>{@link #onOpenFailure(Consumer)} (optional, registers an open-failure callback)
 *   <li>{@link #open()} (presents the UI to the player)
 *   <li>either {@link #close()} or a single {@link ScreenResult} delivered to the callback
 * </ol>
 *
 * <p>Implementations receive {@link #open()} and {@link #close()} invocations on the player's
 * entity scheduler. Result callbacks may be invoked from any thread; CommandPrompter ensures safe
 * thread-hopping, generation token verification, and exactly-once processing.
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

  /**
   * Registers an optional callback for failures that occur after {@link #open()} has scheduled
   * platform work. Existing implementations may ignore this hook; NMS-backed screens use it so
   * wrappers can fall back to chat without leaving a half-open screen.
   *
   * @param callback invoked at most once when asynchronous opening fails
   */
  default void onOpenFailure(Consumer<Throwable> callback) {}
}
