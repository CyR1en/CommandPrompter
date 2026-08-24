package dev.cyr1en.promptcore.plan;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PlanLifecycleStageTest {

  @Test
  void testStageProgressionAndTerminalStatus() {
    assertFalse(PlanLifecycleStage.INPUT_COLLECTION.isTerminal());
    assertFalse(PlanLifecycleStage.PRE_DISPATCH_GATES.isTerminal());
    assertFalse(PlanLifecycleStage.PRIMARY_DISPATCH.isTerminal());
    assertFalse(PlanLifecycleStage.POST_ACTIONS.isTerminal());

    assertTrue(PlanLifecycleStage.CANCEL_ABORT.isTerminal());
    assertTrue(PlanLifecycleStage.TERMINATED.isTerminal());
  }

  @Test
  void testActionTriggerMethods() {
    assertTrue(ActionTrigger.ON_SUCCESS.isOnSuccess());
    assertFalse(ActionTrigger.ON_SUCCESS.isOnCancel());

    assertTrue(ActionTrigger.ON_CANCEL.isOnCancel());
    assertFalse(ActionTrigger.ON_CANCEL.isOnSuccess());
  }
}
