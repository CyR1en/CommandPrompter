package dev.cyr1en.promptpaper.execution.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.PostCommandMeta;
import dev.cyr1en.promptcore.SessionResult;
import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import dev.cyr1en.promptcore.plan.ExecutionPlanDefinition;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InputCompletionTest {

  private ExecutionPlanDefinition samplePlan() {
    return new ExecutionPlanDefinition(
        TemplateCompiler.compile("give {0} diamond {1}"), List.of(), List.of());
  }

  @Test
  @DisplayName("InputCompletion retains values and defensive copy of answers")
  void constructorDefensiveCopyAndGetters() {
    UUID initiator = UUID.randomUUID();
    List<String> mutableAnswers = new ArrayList<>(List.of("player1", "64"));
    PresetSnapshot snapshot = PresetSnapshot.empty();
    DispatchContextSnapshot dispatchContext = DispatchContextSnapshot.player();
    ExecutionPlanDefinition plan = samplePlan();

    InputCompletion completion =
        new InputCompletion(
            initiator, 1, 42L, mutableAnswers, "give player1 diamond 64", plan, snapshot, dispatchContext);

    // Modify original list
    mutableAnswers.add("extra");

    assertEquals(initiator, completion.initiatorUuid());
    assertEquals(1, completion.incarnation());
    assertEquals(42L, completion.finalGeneration());
    assertEquals(2, completion.answers().size());
    assertEquals(List.of("player1", "64"), completion.answers());
    assertEquals("give player1 diamond 64", completion.assembledCommand());
    assertTrue(completion.getAssembledCommand().isPresent());
    assertTrue(completion.getCompiledPlan().isPresent());
    assertEquals(plan, completion.compiledPlan());
    assertSame(snapshot, completion.capturedPresetSnapshot());
    assertSame(dispatchContext, completion.dispatchContext());

    assertThrows(UnsupportedOperationException.class, () -> completion.answers().add("illegal"));
  }

  @Test
  @DisplayName("InputCompletion.of factory correctly bridges SessionResult")
  void factoryFromSessionResult() {
    UUID initiator = UUID.randomUUID();
    SessionResult sessionResult =
        new SessionResult(
            "teleport player1 100 64 200",
            List.of("100", "64", "200"),
            List.<PostCommandMeta>of(),
            List.<PostCommandMeta>of());
    ExecutionPlanDefinition plan = samplePlan();
    PresetSnapshot snapshot = PresetSnapshot.empty();
    DispatchContextSnapshot ctx = DispatchContextSnapshot.player();

    InputCompletion completion =
        InputCompletion.of(initiator, 2, 5L, sessionResult, plan, snapshot, ctx);

    assertEquals(initiator, completion.initiatorUuid());
    assertEquals(2, completion.incarnation());
    assertEquals(5L, completion.finalGeneration());
    assertEquals("teleport player1 100 64 200", completion.assembledCommand());
    assertEquals(List.of("100", "64", "200"), completion.answers());
  }

  @Test
  @DisplayName("Null checks reject invalid constructor arguments")
  void nullValidation() {
    UUID initiator = UUID.randomUUID();
    PresetSnapshot snapshot = PresetSnapshot.empty();
    DispatchContextSnapshot ctx = DispatchContextSnapshot.player();
    ExecutionPlanDefinition plan = samplePlan();

    assertThrows(
        NullPointerException.class,
        () -> new InputCompletion(null, 1, 0L, List.of(), "cmd", plan, snapshot, ctx));
    assertThrows(
        NullPointerException.class,
        () -> new InputCompletion(initiator, 1, 0L, null, "cmd", plan, snapshot, ctx));
    assertThrows(
        NullPointerException.class,
        () -> new InputCompletion(initiator, 1, 0L, List.of(), "cmd", plan, null, ctx));
    assertThrows(
        NullPointerException.class,
        () -> new InputCompletion(initiator, 1, 0L, List.of(), "cmd", plan, snapshot, null));

    // Both assembledCommand and compiledPlan null
    assertThrows(
        IllegalArgumentException.class,
        () -> new InputCompletion(initiator, 1, 0L, List.of(), null, null, snapshot, ctx));
  }
}
