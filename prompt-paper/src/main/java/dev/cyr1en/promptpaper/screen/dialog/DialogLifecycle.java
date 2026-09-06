package dev.cyr1en.promptpaper.screen.dialog;

import dev.cyr1en.promptui.ScreenResult;
import java.util.function.Consumer;

/**
 * Tracks the open/result lifecycle of a native dialog without depending on Paper's versioned dialog
 * API.
 */
public final class DialogLifecycle {

  private Consumer<ScreenResult> callback;
  private boolean open;

  /** Marks the client dialog as open. */
  public void opened() {
    open = true;
  }

  /** Returns whether the dialog is awaiting a terminal result. */
  public boolean isOpen() {
    return open;
  }

  /** Registers the single result callback. */
  public void onResult(Consumer<ScreenResult> callback) {
    this.callback = callback;
  }

  /**
   * Closes the dialog without delivering a result.
   *
   * @return whether this call transitioned an open dialog to closed
   */
  public boolean close(Runnable closeDialog) {
    if (!open) return false;
    open = false;
    callback = null;
    closeDialog.run();
    return true;
  }

  /**
   * Closes the client dialog before delivering its result. The ordering is important because the
   * result callback may immediately open the next dialog in the prompt session.
   *
   * @return whether this call completed an open dialog
   */
  public boolean finish(ScreenResult result, Runnable closeDialog) {
    if (!open) return false;
    open = false;
    var resultCallback = callback;
    callback = null;
    try {
      closeDialog.run();
    } finally {
      if (resultCallback != null) resultCallback.accept(result);
    }
    return true;
  }
}
