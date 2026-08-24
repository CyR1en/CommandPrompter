package dev.cyr1en.promptcore.plan;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.DispatchTarget;
import dev.cyr1en.promptcore.ParsedCommand;
import dev.cyr1en.promptcore.ParserConfig;
import dev.cyr1en.promptcore.PostCommandMeta;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionPlanAdapterTest {

  @Test
  void testFromParsedCommandImmediateAndPreset() {
    PostCommandMeta immediatePcm =
        new PostCommandMeta("say {0}", new int[] {0}, 0, false, DispatchTarget.PLAYER, false);
    PostCommandMeta presetPcm =
        new PostCommandMeta("reward_preset", new int[] {1}, 20, true, DispatchTarget.CONSOLE, true);

    ParsedCommand parsed =
        new ParsedCommand(
            "trade {0}", List.of(), List.of(immediatePcm, presetPcm), ParserConfig.ANGLE_BRACKETS);

    ExecutionPlanDefinition plan = ExecutionPlanAdapter.fromParsedCommand(parsed);

    assertEquals("trade {0}", plan.primaryCommandTemplate().source());
    assertFalse(plan.hasGates());
    assertEquals(2, plan.postActionCount());

    // First action: ImmediateCommand with compiled template
    PostActionSpec action1 = plan.postActions().get(0);
    assertInstanceOf(PostActionSpec.ImmediateCommand.class, action1);
    PostActionSpec.ImmediateCommand imm = (PostActionSpec.ImmediateCommand) action1;
    assertEquals("say {0}", imm.commandTemplate().source());
    assertArrayEquals(new int[] {0}, imm.answerIndices());
    assertEquals(ActionTrigger.ON_SUCCESS, imm.trigger());
    assertEquals(DispatchTarget.PLAYER, imm.target());

    // Second action: Delayed(PresetReference)
    PostActionSpec action2 = plan.postActions().get(1);
    assertInstanceOf(PostActionSpec.Delayed.class, action2);
    PostActionSpec.Delayed delayed = (PostActionSpec.Delayed) action2;
    assertEquals(20, delayed.delayTicks());
    assertEquals(ActionTrigger.ON_CANCEL, delayed.trigger());

    assertInstanceOf(PostActionSpec.PresetReference.class, delayed.delegate());
    PostActionSpec.PresetReference presetRef = (PostActionSpec.PresetReference) delayed.delegate();
    assertEquals("reward_preset", presetRef.presetId());
    assertArrayEquals(new int[] {1}, presetRef.answerIndices());
    assertEquals(ActionTrigger.ON_CANCEL, presetRef.trigger());
    assertEquals(DispatchTarget.CONSOLE, presetRef.target());
  }

  @Test
  void testFromParsedCommandWithGates() {
    PreDispatchGateSpec gate = new PreDispatchGateSpec.Approval("trade_approval");
    ParsedCommand parsed =
        new ParsedCommand("/test", List.of(), List.of(), ParserConfig.ANGLE_BRACKETS);

    ExecutionPlanDefinition plan = ExecutionPlanAdapter.fromParsedCommand(parsed, List.of(gate));

    assertTrue(plan.hasGates());
    assertEquals(1, plan.gateCount());
    assertEquals(gate, plan.preDispatchGates().get(0));
    assertEquals("/test", plan.primaryCommandTemplate().source());
  }

  @Test
  void testFromParsedCommandForwardsGatesDirectly() {
    PreDispatchGateSpec gate1 = new PreDispatchGateSpec.Approval("gate_a");
    PreDispatchGateSpec gate2 = new PreDispatchGateSpec.Approval("gate_b");
    ParsedCommand parsed =
        new ParsedCommand(
            "/trade {0} {1}",
            List.of(), List.of(), List.of(gate1, gate2), ParserConfig.ANGLE_BRACKETS);

    ExecutionPlanDefinition plan = ExecutionPlanAdapter.fromParsedCommand(parsed);

    assertTrue(plan.hasGates());
    assertEquals(2, plan.gateCount());
    assertEquals(List.of(gate1, gate2), plan.preDispatchGates());
    assertEquals("/trade {0} {1}", plan.primaryCommandTemplate().source());
  }

  @Test
  void testRoundTripConversion() {
    PostCommandMeta originalImmediate =
        new PostCommandMeta("say hi", new int[] {0}, 10, true, DispatchTarget.CONSOLE, false);
    PostActionSpec spec = ExecutionPlanAdapter.toPostActionSpec(originalImmediate);
    PostCommandMeta roundtripped = ExecutionPlanAdapter.toPostCommandMeta(spec);

    assertEquals(originalImmediate, roundtripped);

    PostCommandMeta originalPreset =
        new PostCommandMeta("preset_id", new int[] {0, 1}, 0, false, DispatchTarget.PLAYER, true);
    PostActionSpec specPreset = ExecutionPlanAdapter.toPostActionSpec(originalPreset);
    PostCommandMeta roundtrippedPreset = ExecutionPlanAdapter.toPostCommandMeta(specPreset);

    assertEquals(originalPreset, roundtrippedPreset);
  }

  @Test
  void testLegacyPcmWithSeventeenRepeatedPlaceholdersAcceptedAsSingleUniqueReference() {
    String cmd = "say " + "{0} ".repeat(17).trim();
    int[] seventeenZeros = new int[17];
    PostCommandMeta legacyPcm =
        new PostCommandMeta(cmd, seventeenZeros, 0, false, DispatchTarget.PLAYER, false);

    PostActionSpec spec = ExecutionPlanAdapter.toPostActionSpec(legacyPcm);
    assertInstanceOf(PostActionSpec.ImmediateCommand.class, spec);
    PostActionSpec.ImmediateCommand imm = (PostActionSpec.ImmediateCommand) spec;
    assertArrayEquals(new int[] {0}, imm.answerIndices());

    PostCommandMeta roundtripped = ExecutionPlanAdapter.toPostCommandMeta(spec);
    assertEquals(cmd, roundtripped.command());
    assertArrayEquals(new int[] {0}, roundtripped.answerIndices());
    assertEquals(DispatchTarget.PLAYER, roundtripped.dispatchTarget());
    assertFalse(roundtripped.onCancel());
  }

  @Test
  void testLegacyPcmWithMoreThanSixteenDistinctIndicesRejected() {
    int[] seventeenDistinct =
        java.util.stream.IntStream.range(0, PostActionSpec.MAX_ANSWER_INDICES + 1).toArray();
    PostCommandMeta legacyPcm =
        new PostCommandMeta("cmd", seventeenDistinct, 0, false, DispatchTarget.PLAYER, false);

    assertThrows(
        IllegalArgumentException.class, () -> ExecutionPlanAdapter.toPostActionSpec(legacyPcm));
  }

  @Test
  void testConversionDerivesAnswerIndicesFromTemplateWhenEmpty() {
    PostCommandMeta pcm =
        new PostCommandMeta(
            "say {0} {player} {1} {0}", new int[0], 0, false, DispatchTarget.CONSOLE, false);
    PostActionSpec spec = ExecutionPlanAdapter.toPostActionSpec(pcm);
    assertInstanceOf(PostActionSpec.ImmediateCommand.class, spec);
    PostActionSpec.ImmediateCommand imm = (PostActionSpec.ImmediateCommand) spec;
    assertArrayEquals(new int[] {0, 1}, imm.answerIndices());
  }
}
