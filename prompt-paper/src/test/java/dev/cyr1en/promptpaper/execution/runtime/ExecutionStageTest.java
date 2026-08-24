package dev.cyr1en.promptpaper.execution.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.plan.PlanLifecycleStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ExecutionStageTest {

  @Test
  @DisplayName("Terminal stages correctly identified")
  void terminalStageIdentification() {
    assertFalse(ExecutionStage.PRE_DISPATCH_GATES.isTerminal());
    assertFalse(ExecutionStage.PRIMARY_DISPATCH.isTerminal());
    assertFalse(ExecutionStage.POST_ACTIONS.isTerminal());

    assertTrue(ExecutionStage.COMPLETED.isTerminal());
    assertTrue(ExecutionStage.CANCELLED.isTerminal());
    assertTrue(ExecutionStage.ERROR.isTerminal());
  }

  @Test
  @DisplayName("Legal transitions from PRE_DISPATCH_GATES")
  void transitionsFromPreDispatchGates() {
    ExecutionStage stage = ExecutionStage.PRE_DISPATCH_GATES;

    assertTrue(stage.canTransitionTo(ExecutionStage.PRIMARY_DISPATCH));
    assertTrue(stage.canTransitionTo(ExecutionStage.POST_ACTIONS));
    assertTrue(stage.canTransitionTo(ExecutionStage.CANCELLED));
    assertTrue(stage.canTransitionTo(ExecutionStage.ERROR));

    assertFalse(stage.canTransitionTo(ExecutionStage.PRE_DISPATCH_GATES));
    assertFalse(stage.canTransitionTo(ExecutionStage.COMPLETED));
  }

  @Test
  @DisplayName("Legal transitions from PRIMARY_DISPATCH")
  void transitionsFromPrimaryDispatch() {
    ExecutionStage stage = ExecutionStage.PRIMARY_DISPATCH;

    assertTrue(stage.canTransitionTo(ExecutionStage.POST_ACTIONS));
    assertTrue(stage.canTransitionTo(ExecutionStage.COMPLETED));
    assertTrue(stage.canTransitionTo(ExecutionStage.CANCELLED));
    assertTrue(stage.canTransitionTo(ExecutionStage.ERROR));

    assertFalse(stage.canTransitionTo(ExecutionStage.PRE_DISPATCH_GATES));
    assertFalse(stage.canTransitionTo(ExecutionStage.PRIMARY_DISPATCH));
  }

  @Test
  @DisplayName("Legal transitions from POST_ACTIONS")
  void transitionsFromPostActions() {
    ExecutionStage stage = ExecutionStage.POST_ACTIONS;

    assertTrue(stage.canTransitionTo(ExecutionStage.COMPLETED));
    assertTrue(stage.canTransitionTo(ExecutionStage.CANCELLED));
    assertTrue(stage.canTransitionTo(ExecutionStage.ERROR));

    assertFalse(stage.canTransitionTo(ExecutionStage.PRE_DISPATCH_GATES));
    assertFalse(stage.canTransitionTo(ExecutionStage.PRIMARY_DISPATCH));
    assertFalse(stage.canTransitionTo(ExecutionStage.POST_ACTIONS));
  }

  @ParameterizedTest
  @EnumSource(
      value = ExecutionStage.class,
      names = {"COMPLETED", "CANCELLED", "ERROR"})
  @DisplayName("Terminal stages cannot transition to any stage")
  void terminalStagesCannotTransition(ExecutionStage terminalStage) {
    for (ExecutionStage target : ExecutionStage.values()) {
      assertFalse(
          terminalStage.canTransitionTo(target),
          "Terminal stage " + terminalStage + " must not transition to " + target);
    }
  }

  @Test
  @DisplayName("Null transition target throws NullPointerException")
  void nullTransitionThrows() {
    for (ExecutionStage stage : ExecutionStage.values()) {
      assertThrows(NullPointerException.class, () -> stage.canTransitionTo(null));
    }
  }

  @Test
  @DisplayName("Mapping to PlanLifecycleStage produces correct counterparts")
  void toPlanLifecycleStageMapping() {
    assertEquals(PlanLifecycleStage.PRE_DISPATCH_GATES, ExecutionStage.PRE_DISPATCH_GATES.toPlanLifecycleStage());
    assertEquals(PlanLifecycleStage.PRIMARY_DISPATCH, ExecutionStage.PRIMARY_DISPATCH.toPlanLifecycleStage());
    assertEquals(PlanLifecycleStage.POST_ACTIONS, ExecutionStage.POST_ACTIONS.toPlanLifecycleStage());
    assertEquals(PlanLifecycleStage.TERMINATED, ExecutionStage.COMPLETED.toPlanLifecycleStage());
    assertEquals(PlanLifecycleStage.CANCEL_ABORT, ExecutionStage.CANCELLED.toPlanLifecycleStage());
    assertEquals(PlanLifecycleStage.CANCEL_ABORT, ExecutionStage.ERROR.toPlanLifecycleStage());
  }
}
