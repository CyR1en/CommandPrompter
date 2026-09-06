package dev.cyr1en.promptpaper.screen.confirmation;

import java.util.function.Consumer;

/**
 * Internal view contract for presenting a confirmation prompt to a player.
 *
 * <p>Implementations manage concrete presentation mechanics (e.g. Chest GUI, Native Dialog, Chat)
 * and deliver a single {@link ConfirmationOutcome} to the coordinator.
 */
public interface ConfirmationView {

  /**
   * Opens the confirmation view and delivers the terminal outcome to the given callback.
   *
   * @param callback consumer for the single terminal outcome
   */
  void open(Consumer<ConfirmationOutcome> callback);

  /** Closes the confirmation view programmatically without emitting an outcome. */
  void close();

  /**
   * Returns whether the view is currently open and awaiting player interaction.
   *
   * @return true if open
   */
  boolean isOpen();

  /**
   * Registers a failure callback invoked if asynchronous or synchronous opening fails, allowing the
   * coordinator to trigger fallback to another view.
   *
   * @param failureCallback consumer for open errors
   */
  default void onOpenFailure(Consumer<Throwable> failureCallback) {}
}
