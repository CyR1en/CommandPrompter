package dev.cyr1en.promptcore.plan;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.DispatchTarget;
import dev.cyr1en.promptcore.logic.transform.CompiledTemplate;
import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import org.junit.jupiter.api.Test;

class PostActionSpecTest {

  @Test
  void testImmediateCommandValid() {
    int[] indices = new int[] {0, 1};
    CompiledTemplate compiled = TemplateCompiler.compile("say {0} {1}");
    PostActionSpec.ImmediateCommand cmd =
        new PostActionSpec.ImmediateCommand(
            compiled, indices, ActionTrigger.ON_SUCCESS, DispatchTarget.CONSOLE);

    assertEquals(compiled, cmd.commandTemplate());
    assertEquals("say {0} {1}", cmd.commandTemplate().source());
    assertArrayEquals(new int[] {0, 1}, cmd.answerIndices());
    assertEquals(ActionTrigger.ON_SUCCESS, cmd.trigger());
    assertEquals(DispatchTarget.CONSOLE, cmd.target());
    assertEquals(1, cmd.nodeCount());

    // Defensive copy check
    indices[0] = 99;
    assertEquals(0, cmd.answerIndices()[0]);
    cmd.answerIndices()[0] = 99;
    assertEquals(0, cmd.answerIndices()[0]);
  }

  @Test
  void testImmediateCommandEqualityAndHashCode() {
    CompiledTemplate compiled = TemplateCompiler.compile("say {0}");
    PostActionSpec.ImmediateCommand cmd1 =
        new PostActionSpec.ImmediateCommand(
            compiled, new int[] {0}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER);
    PostActionSpec.ImmediateCommand cmd2 =
        new PostActionSpec.ImmediateCommand(
            compiled, new int[] {0}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER);
    PostActionSpec.ImmediateCommand cmd3 =
        new PostActionSpec.ImmediateCommand(
            compiled, new int[] {1}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER);

    assertEquals(cmd1, cmd2);
    assertEquals(cmd1.hashCode(), cmd2.hashCode());
    assertNotEquals(cmd1, cmd3);
    assertTrue(cmd1.toString().contains("say {0}"));
  }

  @Test
  void testImmediateCommandBoundsAndValidation() {
    CompiledTemplate compiled = TemplateCompiler.compile("cmd");

    assertThrows(
        NullPointerException.class,
        () ->
            new PostActionSpec.ImmediateCommand(
                null, new int[] {}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER));
    assertThrows(
        NullPointerException.class,
        () ->
            new PostActionSpec.ImmediateCommand(
                compiled, null, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER));
    assertThrows(
        NullPointerException.class,
        () ->
            new PostActionSpec.ImmediateCommand(
                compiled, new int[] {}, null, DispatchTarget.PLAYER));
    assertThrows(
        NullPointerException.class,
        () ->
            new PostActionSpec.ImmediateCommand(
                compiled, new int[] {}, ActionTrigger.ON_SUCCESS, null));

    int[] tooManyDistinctIndices =
        java.util.stream.IntStream.range(0, PostActionSpec.MAX_ANSWER_INDICES + 1).toArray();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PostActionSpec.ImmediateCommand(
                compiled, tooManyDistinctIndices, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PostActionSpec.ImmediateCommand(
                compiled, new int[] {-1}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER));
  }

  @Test
  void testImmediateCommandRepeatedIndicesDeduplicatedPreservingOrder() {
    CompiledTemplate compiled = TemplateCompiler.compile("say {0}");
    // 17 repeated 0s should be accepted and canonicalized to a single [0]
    int[] seventeenZeros = new int[17];
    PostActionSpec.ImmediateCommand cmd =
        new PostActionSpec.ImmediateCommand(
            compiled, seventeenZeros, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER);
    assertArrayEquals(new int[] {0}, cmd.answerIndices());

    // Deduplication preserving first-seen order
    PostActionSpec.ImmediateCommand orderedCmd =
        new PostActionSpec.ImmediateCommand(
            compiled,
            new int[] {2, 0, 2, 1, 0, 3},
            ActionTrigger.ON_SUCCESS,
            DispatchTarget.PLAYER);
    assertArrayEquals(new int[] {2, 0, 1, 3}, orderedCmd.answerIndices());
  }

  @Test
  void testImmediateCommandDerivesAnswerIndicesFromCompiledTemplate() {
    CompiledTemplate compiled = TemplateCompiler.compile("say {0} {player} {0} {1} {target}");
    PostActionSpec.ImmediateCommand cmd =
        new PostActionSpec.ImmediateCommand(
            compiled, ActionTrigger.ON_SUCCESS, DispatchTarget.CONSOLE);
    assertArrayEquals(new int[] {0, 1}, cmd.answerIndices());
  }

  @Test
  void testPresetReferenceValid() {
    int[] indices = new int[] {2};
    PostActionSpec.PresetReference preset =
        new PostActionSpec.PresetReference(
            "reward_user", indices, ActionTrigger.ON_CANCEL, DispatchTarget.PASSTHROUGH);

    assertEquals("reward_user", preset.presetId());
    assertArrayEquals(new int[] {2}, preset.answerIndices());
    assertEquals(ActionTrigger.ON_CANCEL, preset.trigger());
    assertEquals(DispatchTarget.PASSTHROUGH, preset.target());
    assertEquals(1, preset.nodeCount());

    // Defensive copy check
    indices[0] = 99;
    assertEquals(2, preset.answerIndices()[0]);
  }

  @Test
  void testPresetReferenceEqualityAndHashCode() {
    PostActionSpec.PresetReference p1 =
        new PostActionSpec.PresetReference(
            "p1", new int[] {0}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER);
    PostActionSpec.PresetReference p2 =
        new PostActionSpec.PresetReference(
            "p1", new int[] {0}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER);
    PostActionSpec.PresetReference p3 =
        new PostActionSpec.PresetReference(
            "p2", new int[] {0}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER);

    assertEquals(p1, p2);
    assertEquals(p1.hashCode(), p2.hashCode());
    assertNotEquals(p1, p3);
    assertTrue(p1.toString().contains("p1"));
  }

  @Test
  void testPresetReferenceBoundsAndValidation() {
    assertThrows(
        NullPointerException.class,
        () ->
            new PostActionSpec.PresetReference(
                null, new int[] {}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PostActionSpec.PresetReference(
                "", new int[] {}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PostActionSpec.PresetReference(
                "invalid id with space",
                new int[] {},
                ActionTrigger.ON_SUCCESS,
                DispatchTarget.PLAYER));

    String oversizedId = "a".repeat(PostActionSpec.MAX_PRESET_ID_LENGTH + 1);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PostActionSpec.PresetReference(
                oversizedId, new int[] {}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER));

    int[] tooManyDistinctIndices =
        java.util.stream.IntStream.range(0, PostActionSpec.MAX_ANSWER_INDICES + 1).toArray();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PostActionSpec.PresetReference(
                "valid_id",
                tooManyDistinctIndices,
                ActionTrigger.ON_SUCCESS,
                DispatchTarget.PLAYER));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PostActionSpec.PresetReference(
                "valid_id", new int[] {-1}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER));
  }

  @Test
  void testPresetReferenceRepeatedIndicesDeduplicatedPreservingOrder() {
    int[] seventeenZeros = new int[17];
    PostActionSpec.PresetReference preset =
        new PostActionSpec.PresetReference(
            "preset_id", seventeenZeros, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER);
    assertArrayEquals(new int[] {0}, preset.answerIndices());

    PostActionSpec.PresetReference orderedPreset =
        new PostActionSpec.PresetReference(
            "preset_id",
            new int[] {3, 1, 3, 2, 1, 0},
            ActionTrigger.ON_SUCCESS,
            DispatchTarget.PLAYER);
    assertArrayEquals(new int[] {3, 1, 2, 0}, orderedPreset.answerIndices());
  }

  @Test
  void testDelayedAction() {
    CompiledTemplate compiled = TemplateCompiler.compile("tell raw");
    PostActionSpec.ImmediateCommand inner =
        new PostActionSpec.ImmediateCommand(
            compiled, new int[] {}, ActionTrigger.ON_SUCCESS, DispatchTarget.PLAYER);
    PostActionSpec.Delayed delayed = new PostActionSpec.Delayed(inner, 20);

    assertEquals(inner, delayed.delegate());
    assertEquals(20, delayed.delayTicks());
    assertEquals(ActionTrigger.ON_SUCCESS, delayed.trigger());
    assertEquals(2, delayed.nodeCount());

    // Null delegate
    assertThrows(NullPointerException.class, () -> new PostActionSpec.Delayed(null, 10));

    // Null trigger in canonical constructor
    assertThrows(NullPointerException.class, () -> new PostActionSpec.Delayed(inner, 10, null));

    // Trigger mismatch rejected
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostActionSpec.Delayed(inner, 10, ActionTrigger.ON_CANCEL));

    // Nested delayed rejected
    assertThrows(IllegalArgumentException.class, () -> new PostActionSpec.Delayed(delayed, 10));

    // Bounds check
    assertThrows(IllegalArgumentException.class, () -> new PostActionSpec.Delayed(inner, 0));
    assertThrows(IllegalArgumentException.class, () -> new PostActionSpec.Delayed(inner, -5));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostActionSpec.Delayed(inner, PostActionSpec.MAX_DELAY_TICKS + 1));
  }
}
