package dev.cyr1en.promptpaper.execution.coordinator;

import dev.cyr1en.promptcore.plan.PreDispatchGateSpec;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionPlanInstance;
import org.bukkit.entity.Player;

/**
 * Narrow contract for evaluating pre-dispatch gates.
 *
 * <p>Used by {@link ExecutionCoordinator} to delegate gate evaluation without direct coupling or
 * cyclical dependencies with specific coordinator implementations.
 */
@FunctionalInterface
public interface PreDispatchGateHandler {

  /**
   * Evaluates a pre-dispatch gate specification.
   *
   * @param initiatorPlayer initiator player
   * @param instance executing plan instance
   * @param gateSpec gate specification to evaluate
   * @param gateIndex index of this gate in the plan's gate sequence
   * @param callback completion callback
   */
  void evaluateGate(
      Player initiatorPlayer,
      ExecutionPlanInstance instance,
      PreDispatchGateSpec gateSpec,
      int gateIndex,
      PreDispatchGateCallback callback);
}
