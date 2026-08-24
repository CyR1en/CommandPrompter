package dev.cyr1en.promptcore.plan;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.DispatchTarget;
import dev.cyr1en.promptcore.logic.transform.CompiledTemplate;
import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionPlanDefinitionTest {

  @Test
  void testValidConstructionAndAccessors() {
    PreDispatchGateSpec gate = new PreDispatchGateSpec.Approval("trade_approval");
    CompiledTemplate immediateTemplate = TemplateCompiler.compile("say hello");
    PostActionSpec action =
        new PostActionSpec.ImmediateCommand(
            immediateTemplate, new int[] {0}, ActionTrigger.ON_SUCCESS, DispatchTarget.CONSOLE);
    CompiledTemplate primaryTemplate = TemplateCompiler.compile("/give player diamond");

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(primaryTemplate, List.of(gate), List.of(action));

    assertEquals(primaryTemplate, plan.primaryCommandTemplate());
    assertEquals("/give player diamond", plan.primaryCommandTemplate().source());
    assertTrue(plan.hasGates());
    assertTrue(plan.hasPostActions());
    assertEquals(1, plan.gateCount());
    assertEquals(1, plan.postActionCount());
    assertEquals(1, plan.totalPostActionNodes());
    assertEquals(List.of(gate), plan.preDispatchGates());
    assertEquals(List.of(action), plan.postActions());
  }

  @Test
  void testEmptyGatesAndActions() {
    CompiledTemplate primaryTemplate = TemplateCompiler.compile("/help");
    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(primaryTemplate, List.of(), List.of());

    assertEquals(primaryTemplate, plan.primaryCommandTemplate());
    assertEquals("/help", plan.primaryCommandTemplate().source());
    assertFalse(plan.hasGates());
    assertFalse(plan.hasPostActions());
    assertEquals(0, plan.gateCount());
    assertEquals(0, plan.postActionCount());
    assertEquals(0, plan.totalPostActionNodes());
  }

  @Test
  void testNullChecks() {
    CompiledTemplate primary = TemplateCompiler.compile("/test");
    assertThrows(
        NullPointerException.class, () -> new ExecutionPlanDefinition(null, List.of(), List.of()));
    assertThrows(
        NullPointerException.class, () -> new ExecutionPlanDefinition(primary, null, List.of()));
    assertThrows(
        NullPointerException.class, () -> new ExecutionPlanDefinition(primary, List.of(), null));
  }

  @Test
  void testImmutabilityAndDefensiveCopying() {
    List<PreDispatchGateSpec> mutableGates = new ArrayList<>();
    mutableGates.add(new PreDispatchGateSpec.Approval("gate1"));

    CompiledTemplate cmd1 = TemplateCompiler.compile("cmd1");
    List<PostActionSpec> mutableActions = new ArrayList<>();
    mutableActions.add(
        new PostActionSpec.ImmediateCommand(
            cmd1, new int[] {0}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER));

    CompiledTemplate primary = TemplateCompiler.compile("/cmd");
    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(primary, mutableGates, mutableActions);

    mutableGates.add(new PreDispatchGateSpec.Approval("gate2"));
    CompiledTemplate cmd2 = TemplateCompiler.compile("cmd2");
    mutableActions.add(
        new PostActionSpec.ImmediateCommand(
            cmd2, new int[] {1}, ActionTrigger.ON_CANCEL, DispatchTarget.CONSOLE));

    assertEquals(1, plan.gateCount());
    assertEquals(1, plan.postActionCount());

    assertThrows(
        UnsupportedOperationException.class,
        () -> plan.preDispatchGates().add(new PreDispatchGateSpec.Approval("gate3")));
    CompiledTemplate cmd3 = TemplateCompiler.compile("cmd3");
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            plan.postActions()
                .add(
                    new PostActionSpec.ImmediateCommand(
                        cmd3, new int[] {}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER)));
  }

  @Test
  void testBoundsEnforced() {
    CompiledTemplate primary = TemplateCompiler.compile("/test");

    List<PreDispatchGateSpec> tooManyGates =
        Collections.nCopies(
            ExecutionPlanDefinition.MAX_GATES + 1, new PreDispatchGateSpec.Approval("gate"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExecutionPlanDefinition(primary, tooManyGates, List.of()));

    CompiledTemplate cmd = TemplateCompiler.compile("cmd");
    List<PostActionSpec> tooManyActions =
        Collections.nCopies(
            ExecutionPlanDefinition.MAX_POST_ACTIONS + 1,
            new PostActionSpec.ImmediateCommand(
                cmd, new int[] {}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExecutionPlanDefinition(primary, List.of(), tooManyActions));
  }

  @Test
  void testGlobalPostActionNodeCountEnforcedForNestedDelayed() {
    CompiledTemplate primary = TemplateCompiler.compile("/test");
    CompiledTemplate cmd = TemplateCompiler.compile("cmd");
    PostActionSpec immediate =
        new PostActionSpec.ImmediateCommand(
            cmd, new int[] {}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER);
    PostActionSpec delayed = new PostActionSpec.Delayed(immediate, 10);

    // Each delayed action counts as 2 nodes (Delayed + Immediate).
    // 33 delayed actions = 66 total nodes > 64 MAX_POST_ACTIONS, even though list size (33) <= 64.
    List<PostActionSpec> actions = Collections.nCopies(33, delayed);
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExecutionPlanDefinition(primary, List.of(), actions));

    // 32 delayed actions = 64 total nodes <= 64 MAX_POST_ACTIONS (allowed).
    List<PostActionSpec> validActions = Collections.nCopies(32, delayed);
    ExecutionPlanDefinition plan = new ExecutionPlanDefinition(primary, List.of(), validActions);
    assertEquals(32, plan.postActionCount());
    assertEquals(64, plan.totalPostActionNodes());
  }
}
