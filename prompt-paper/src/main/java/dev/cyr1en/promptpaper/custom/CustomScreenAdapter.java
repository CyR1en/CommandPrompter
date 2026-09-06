package dev.cyr1en.promptpaper.custom;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Adapter wrapping a custom/third-party {@link InputScreen} delegate to enforce region-scheduler
 * safety, authoritative session verification, exactly-once delivery, and provider lifecycle
 * containment.
 */
public class CustomScreenAdapter implements InputScreen {

  private final CustomScreenHandle handle;
  private final AtomicReference<InputScreen> delegateRef;
  private final AtomicReference<Supplier<InputScreen>> delegateSupplierRef;
  private final PlayerExecutor playerExecutor;
  private final SessionVerificationSnapshot snapshot;
  private final ScreenAttemptVerifier verifier;

  private final AtomicBoolean terminalDelivered = new AtomicBoolean(false);
  private final AtomicBoolean detached = new AtomicBoolean(false);
  private final AtomicBoolean delegateCallbacksRegistered = new AtomicBoolean(false);

  private final AtomicReference<Consumer<ScreenResult>> resultCallbackRef = new AtomicReference<>();
  private final AtomicReference<Consumer<Throwable>> openFailureCallbackRef =
      new AtomicReference<>();
  private final AtomicReference<Runnable> retirementCallbackRef = new AtomicReference<>();

  /**
   * Constructs a new {@link CustomScreenAdapter} with an existing delegate instance.
   *
   * @param handle the registration handle / provider token
   * @param delegate the third-party input screen delegate (may be null if construction failed)
   * @param playerExecutor executor for scheduling work on the target player's region/thread
   * @param snapshot immutable session contract expected for this attempt
   * @param verifier post-hop callback reading authoritative live session state
   * @param retirementCallback optional callback invoked when the scheduler is retired
   */
  public CustomScreenAdapter(
      CustomScreenHandle handle,
      InputScreen delegate,
      PlayerExecutor playerExecutor,
      SessionVerificationSnapshot snapshot,
      ScreenAttemptVerifier verifier,
      Runnable retirementCallback) {
    this.handle = Objects.requireNonNull(handle, "handle");
    this.delegateRef = new AtomicReference<>(delegate);
    this.delegateSupplierRef = new AtomicReference<>(null);
    this.playerExecutor = Objects.requireNonNull(playerExecutor, "playerExecutor");
    this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    this.verifier = Objects.requireNonNull(verifier, "verifier");
    this.retirementCallbackRef.set(retirementCallback);
  }

  /**
   * Constructs a new {@link CustomScreenAdapter} with an existing delegate instance without a
   * retirement callback.
   */
  public CustomScreenAdapter(
      CustomScreenHandle handle,
      InputScreen delegate,
      PlayerExecutor playerExecutor,
      SessionVerificationSnapshot snapshot,
      ScreenAttemptVerifier verifier) {
    this(handle, delegate, playerExecutor, snapshot, verifier, null);
  }

  private CustomScreenAdapter(
      CustomScreenHandle handle,
      Supplier<InputScreen> delegateSupplier,
      PlayerExecutor playerExecutor,
      SessionVerificationSnapshot snapshot,
      ScreenAttemptVerifier verifier,
      Runnable retirementCallback) {
    this.handle = Objects.requireNonNull(handle, "handle");
    this.delegateRef = new AtomicReference<>(null);
    this.delegateSupplierRef =
        new AtomicReference<>(Objects.requireNonNull(delegateSupplier, "delegateSupplier"));
    this.playerExecutor = Objects.requireNonNull(playerExecutor, "playerExecutor");
    this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    this.verifier = Objects.requireNonNull(verifier, "verifier");
    this.retirementCallbackRef.set(retirementCallback);
  }

  /**
   * Creates a {@link CustomScreenAdapter} with a lazy delegate supplier invoked on the player's
   * thread.
   *
   * @param handle the registration handle / provider token
   * @param delegateSupplier supplier that creates the third-party input screen delegate on the
   *     player thread
   * @param playerExecutor executor for scheduling work on the target player's region/thread
   * @param snapshot immutable session contract expected for this attempt
   * @param verifier post-hop callback reading authoritative live session state
   * @param retirementCallback optional callback invoked when the scheduler is retired
   * @return a new lazy CustomScreenAdapter instance
   */
  public static CustomScreenAdapter lazy(
      CustomScreenHandle handle,
      Supplier<InputScreen> delegateSupplier,
      PlayerExecutor playerExecutor,
      SessionVerificationSnapshot snapshot,
      ScreenAttemptVerifier verifier,
      Runnable retirementCallback) {
    return new CustomScreenAdapter(
        handle, delegateSupplier, playerExecutor, snapshot, verifier, retirementCallback);
  }

  /**
   * Creates a {@link CustomScreenAdapter} with a lazy delegate supplier without a retirement
   * callback.
   */
  public static CustomScreenAdapter lazy(
      CustomScreenHandle handle,
      Supplier<InputScreen> delegateSupplier,
      PlayerExecutor playerExecutor,
      SessionVerificationSnapshot snapshot,
      ScreenAttemptVerifier verifier) {
    return lazy(handle, delegateSupplier, playerExecutor, snapshot, verifier, null);
  }

  @Override
  public void open() {
    bindDelegateCallbacks();

    playerExecutor.execute(
        () -> {
          // Advisory & provider state check after hop
          if (detached.get() || !handle.isActive()) {
            if (terminalDelivered.compareAndSet(false, true)) {
              Consumer<ScreenResult> resultCb = resultCallbackRef.get();
              if (resultCb != null) {
                try {
                  resultCb.accept(ScreenResult.cancel(CancelReason.MANUAL));
                } catch (Throwable ignored) {
                }
              }
            }
            return;
          }

          // Post-hop authoritative session verification
          if (!verifier.verify(snapshot)) {
            return;
          }

          InputScreen delegate = delegateRef.get();
          if (delegate == null) {
            Supplier<InputScreen> supplier = delegateSupplierRef.get();
            if (supplier != null) {
              try {
                delegate = handle.callIfActive(supplier).orElse(null);
                if (delegate == null) {
                  if (terminalDelivered.compareAndSet(false, true)) {
                    deliverError(
                        new IllegalStateException(
                            "Custom screen factory returned null or provider inactive"));
                  }
                  return;
                }
                delegateRef.set(delegate);
                bindDelegateCallbacks();
              } catch (Throwable t) {
                if (terminalDelivered.compareAndSet(false, true)) {
                  deliverError(t);
                }
                return;
              }
            }
          }

          if (delegate == null) {
            if (terminalDelivered.compareAndSet(false, true)) {
              deliverError(new IllegalStateException("Custom screen delegate is null"));
            }
            return;
          }

          InputScreen finalDelegate = delegate;
          boolean opened =
              handle.runIfActive(
                  () -> {
                    try {
                      finalDelegate.open();
                    } catch (Throwable t) {
                      if (terminalDelivered.compareAndSet(false, true)) {
                        deliverError(t);
                      }
                    }
                  });

          if (!opened) {
            if (terminalDelivered.compareAndSet(false, true)) {
              Consumer<ScreenResult> resultCb = resultCallbackRef.get();
              if (resultCb != null) {
                try {
                  resultCb.accept(ScreenResult.cancel(CancelReason.MANUAL));
                } catch (Throwable ignored) {
                }
              }
            }
          }
        },
        this::handleRetirement);
  }

  @Override
  public void close() {
    playerExecutor.execute(
        () -> {
          if (detached.get() || !handle.isActive()) {
            return;
          }
          InputScreen delegate = delegateRef.get();
          if (delegate != null) {
            handle.runIfActive(
                () -> {
                  try {
                    delegate.close();
                  } catch (Throwable ignored) {
                    // Contained; provider close exception must not escape the region task
                  }
                });
          }
        },
        this::handleRetirement);
  }

  @Override
  public boolean isOpen() {
    if (detached.get() || !handle.isActive()) {
      return false;
    }
    InputScreen delegate = delegateRef.get();
    if (delegate == null) {
      return false;
    }
    return handle
        .callIfActive(
            () -> {
              try {
                return delegate.isOpen();
              } catch (Throwable t) {
                return false;
              }
            })
        .orElse(false);
  }

  @Override
  public void onResult(Consumer<ScreenResult> callback) {
    this.resultCallbackRef.set(callback);
    bindDelegateCallbacks();
  }

  @Override
  public void onOpenFailure(Consumer<Throwable> callback) {
    this.openFailureCallbackRef.set(callback);
    bindDelegateCallbacks();
  }

  /**
   * Sets an internal callback to be notified if the player scheduler is retired.
   *
   * @param callback the runnable to invoke on retirement
   */
  public void setRetirementCallback(Runnable callback) {
    this.retirementCallbackRef.set(callback);
  }

  /**
   * Atomically detaches delegate and callback references during provider unregistration / teardown
   * without invoking any delegate methods (such as {@link #close()} or {@link #isOpen()}).
   * Subsequent queued callbacks, open, or close operations will be silently discarded.
   */
  public void teardownDetach() {
    if (detached.compareAndSet(false, true)) {
      delegateRef.set(null);
      delegateSupplierRef.set(null);
      resultCallbackRef.set(null);
      openFailureCallbackRef.set(null);
      retirementCallbackRef.set(null);
    }
  }

  /** Returns the registration handle associated with this adapter. */
  public CustomScreenHandle handle() {
    return handle;
  }

  /** Returns the wrapped delegate, or {@code null} if detached. */
  public InputScreen delegate() {
    return delegateRef.get();
  }

  /** Returns the session verification snapshot. */
  public SessionVerificationSnapshot snapshot() {
    return snapshot;
  }

  /** Returns whether this adapter has been detached. */
  public boolean isDetached() {
    return detached.get();
  }

  /** Returns whether a terminal event (result or error) has already been delivered. */
  public boolean isTerminalDelivered() {
    return terminalDelivered.get();
  }

  private void bindDelegateCallbacks() {
    if (detached.get() || !handle.isActive()) {
      return;
    }
    InputScreen delegate = delegateRef.get();
    if (delegate != null && delegateCallbacksRegistered.compareAndSet(false, true)) {
      boolean bound =
          handle.runIfActive(
              () -> {
                try {
                  delegate.onResult(this::handleDelegateResult);
                } catch (Throwable t) {
                  handleRegistrationError(t);
                  return;
                }
                try {
                  delegate.onOpenFailure(this::handleDelegateOpenFailure);
                } catch (Throwable t) {
                  handleRegistrationError(t);
                }
              });
      if (!bound) {
        delegateCallbacksRegistered.set(false);
      }
    }
  }

  private void handleRegistrationError(Throwable t) {
    playerExecutor.execute(
        () -> {
          if (!detached.get() && terminalDelivered.compareAndSet(false, true)) {
            deliverError(t);
          }
        },
        this::handleRetirement);
  }

  private void handleDelegateResult(ScreenResult result) {
    if (detached.get() || !handle.isActive()) {
      return;
    }

    playerExecutor.execute(
        () -> {
          if (detached.get() || !handle.isActive()) {
            return;
          }

          if (!verifier.verify(snapshot)) {
            return;
          }

          if (!terminalDelivered.compareAndSet(false, true)) {
            return;
          }

          Consumer<ScreenResult> callback = resultCallbackRef.get();
          if (callback != null) {
            try {
              callback.accept(result != null ? result : ScreenResult.error());
            } catch (Throwable ignored) {
              // Contained
            }
          }
        },
        this::handleRetirement);
  }

  private void handleDelegateOpenFailure(Throwable error) {
    if (detached.get() || !handle.isActive()) {
      return;
    }

    playerExecutor.execute(
        () -> {
          if (detached.get() || !handle.isActive()) {
            return;
          }

          if (!verifier.verify(snapshot)) {
            return;
          }

          if (!terminalDelivered.compareAndSet(false, true)) {
            return;
          }

          deliverError(
              error != null ? error : new IllegalStateException("Open failure with null error"));
        },
        this::handleRetirement);
  }

  private void deliverError(Throwable error) {
    Consumer<Throwable> openFailureCb = openFailureCallbackRef.get();
    if (openFailureCb != null) {
      try {
        openFailureCb.accept(error);
      } catch (Throwable ignored) {
      }
    } else {
      Consumer<ScreenResult> resultCb = resultCallbackRef.get();
      if (resultCb != null) {
        try {
          resultCb.accept(ScreenResult.error());
        } catch (Throwable ignored) {
        }
      }
    }
  }

  private void handleRetirement() {
    detached.set(true);
    delegateRef.set(null);
    delegateSupplierRef.set(null);
    resultCallbackRef.set(null);
    openFailureCallbackRef.set(null);

    Runnable callback = retirementCallbackRef.getAndSet(null);
    if (callback != null) {
      try {
        callback.run();
      } catch (Throwable ignored) {
      }
    }
  }
}
