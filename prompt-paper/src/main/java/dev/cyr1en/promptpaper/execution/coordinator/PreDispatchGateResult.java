package dev.cyr1en.promptpaper.execution.coordinator;

import dev.cyr1en.promptpaper.preset.TrustedPresetAction;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of evaluating a pre-dispatch gate.
 *
 * @param status the outcome status of the gate evaluation
 * @param onDenyAction optional immediate action to execute upon denial, timeout, or target
 *     disconnect
 * @param detail diagnostic error detail if status is {@link Status#ERROR}
 */
public record PreDispatchGateResult(
    Status status, TrustedPresetAction onDenyAction, String detail) {

  public enum Status {
    APPROVED,
    DENIED,
    TIMED_OUT,
    TARGET_DISCONNECTED,
    INITIATOR_DISCONNECTED,
    ERROR;

    public boolean isApproved() {
      return this == APPROVED;
    }
  }

  public PreDispatchGateResult {
    Objects.requireNonNull(status, "status must not be null");
  }

  public static PreDispatchGateResult approved() {
    return new PreDispatchGateResult(Status.APPROVED, null, null);
  }

  public static PreDispatchGateResult denied(TrustedPresetAction onDenyAction) {
    return new PreDispatchGateResult(Status.DENIED, onDenyAction, null);
  }

  public static PreDispatchGateResult timedOut(TrustedPresetAction onDenyAction) {
    return new PreDispatchGateResult(Status.TIMED_OUT, onDenyAction, null);
  }

  public static PreDispatchGateResult targetDisconnected(TrustedPresetAction onDenyAction) {
    return new PreDispatchGateResult(Status.TARGET_DISCONNECTED, onDenyAction, null);
  }

  public static PreDispatchGateResult initiatorDisconnected() {
    return new PreDispatchGateResult(Status.INITIATOR_DISCONNECTED, null, null);
  }

  public static PreDispatchGateResult error(String detail) {
    return new PreDispatchGateResult(Status.ERROR, null, detail);
  }

  public Optional<TrustedPresetAction> getOnDenyAction() {
    return Optional.ofNullable(onDenyAction);
  }
}
