package dev.cyr1en.promptpaper.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cyr1en.promptcore.logic.condition.ConditionCompiler;
import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PresetSnapshotTest {

  @Test
  void emptySnapshotReturnsCanonicalEmpty() {
    var empty = PresetSnapshot.empty();
    assertNotNull(empty);
    assertTrue(empty.isEmpty());
    assertEquals(0, empty.totalCount());
    assertEquals(0, empty.promptCount());
    assertEquals(0, empty.postCommandCount());
    assertEquals(0, empty.approvalGateCount());
    assertEquals(0, empty.conditionalPostCommandCount());
    assertEquals(0L, empty.generation());
    assertTrue(empty.getPrompt("any").isEmpty());
    assertTrue(empty.getPostCommand("any").isEmpty());
    assertTrue(empty.getApprovalGate("any").isEmpty());
    assertTrue(empty.getConditionalPostCommand("any").isEmpty());
    assertTrue(empty.getPromptIds().isEmpty());
    assertTrue(empty.getPostCommandIds().isEmpty());
    assertTrue(empty.getApprovalGateIds().isEmpty());
    assertTrue(empty.getConditionalPostCommandIds().isEmpty());
  }

  @Test
  void deepImmutabilityAndOrderPreservation() {
    var prompt1 =
        new ChatPrompt(
            "chat",
            "p1",
            "Text 1",
            new CancelBehavior(false, "", false, ""),
            true);
    var prompt2 =
        new ChatPrompt(
            "chat",
            "p2",
            "Text 2",
            new CancelBehavior(false, "", false, ""),
            true);

    Map<String, PromptDefinition> mutablePrompts = new LinkedHashMap<>();
    mutablePrompts.put("p1", prompt1);
    mutablePrompts.put("p2", prompt2);

    var post1 = new PostCommand("cmd1", "say hi", ExecutionPolicy.ON_COMPLETE, ExecuteAs.CONSOLE, 0);
    Map<String, PostCommand> mutablePosts = new LinkedHashMap<>();
    mutablePosts.put("cmd1", post1);

    var gate1 =
        new ApprovalGateDefinition("gate1", TemplateCompiler.compile("admin"), TemplateCompiler.compile("msg"));
    Map<String, ApprovalGateDefinition> mutableGates = new LinkedHashMap<>();
    mutableGates.put("gate1", gate1);

    var cond1 =
        new ConditionalPostCommandDefinition(
            "cond1",
            ConditionCompiler.compile("{0} == 1", dev.cyr1en.promptcore.logic.condition.ConditionCompileOptions.forPreset()),
            ExecutionPolicy.ON_COMPLETE,
            TrustedPresetAction.of("say true", ExecuteAs.CONSOLE, 0),
            null);
    Map<String, ConditionalPostCommandDefinition> mutableConds = new LinkedHashMap<>();
    mutableConds.put("cond1", cond1);

    var snapshot = new PresetSnapshot(mutablePrompts, mutablePosts, mutableGates, mutableConds, 5L);

    // Modify original map -> snapshot must not change
    mutablePrompts.put(
        "p3",
        new ChatPrompt("chat", "p3", "Text 3", new CancelBehavior(false, "", false, ""), true));
    assertEquals(2, snapshot.promptCount());
    assertEquals(List.of("p1", "p2"), new ArrayList<>(snapshot.getPromptIds()));

    // Snapshot maps and keySets must be unmodifiable
    assertThrows(UnsupportedOperationException.class, () -> snapshot.prompts().put("x", prompt1));
    assertThrows(UnsupportedOperationException.class, () -> snapshot.postCommands().put("x", post1));
    assertThrows(UnsupportedOperationException.class, () -> snapshot.approvalGates().put("x", gate1));
    assertThrows(UnsupportedOperationException.class, () -> snapshot.conditionalPostCommands().put("x", cond1));
    assertThrows(UnsupportedOperationException.class, () -> snapshot.getPromptIds().add("x"));
    assertThrows(UnsupportedOperationException.class, () -> snapshot.getPostCommandIds().add("x"));
    assertThrows(UnsupportedOperationException.class, () -> snapshot.getApprovalGateIds().add("x"));
    assertThrows(UnsupportedOperationException.class, () -> snapshot.getConditionalPostCommandIds().add("x"));

    // Lookups
    assertTrue(snapshot.getPrompt("p1").isPresent());
    assertSame(prompt1, snapshot.getPrompt("p1").get());
    assertTrue(snapshot.getPrompt("p2").isPresent());
    assertSame(prompt2, snapshot.getPrompt("p2").get());
    assertTrue(snapshot.getPrompt("p3").isEmpty());
    assertTrue(snapshot.getPrompt(null).isEmpty());

    assertTrue(snapshot.getPostCommand("cmd1").isPresent());
    assertSame(post1, snapshot.getPostCommand("cmd1").get());
    assertTrue(snapshot.getPostCommand("none").isEmpty());
    assertTrue(snapshot.getPostCommand(null).isEmpty());

    assertTrue(snapshot.getApprovalGate("gate1").isPresent());
    assertSame(gate1, snapshot.getApprovalGate("gate1").get());
    assertTrue(snapshot.getApprovalGate("none").isEmpty());
    assertTrue(snapshot.getApprovalGate(null).isEmpty());

    assertTrue(snapshot.getConditionalPostCommand("cond1").isPresent());
    assertSame(cond1, snapshot.getConditionalPostCommand("cond1").get());
    assertTrue(snapshot.getConditionalPostCommand("none").isEmpty());
    assertTrue(snapshot.getConditionalPostCommand(null).isEmpty());

    assertFalse(snapshot.isEmpty());
    assertEquals(5, snapshot.totalCount());
    assertEquals(5L, snapshot.generation());
  }

  @Test
  void nullMapsThrowNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> new PresetSnapshot(null, Map.of(), Map.of(), Map.of(), 1L));
    assertThrows(
        NullPointerException.class,
        () -> new PresetSnapshot(Map.of(), null, Map.of(), Map.of(), 1L));
    assertThrows(
        NullPointerException.class,
        () -> new PresetSnapshot(Map.of(), Map.of(), null, Map.of(), 1L));
    assertThrows(
        NullPointerException.class,
        () -> new PresetSnapshot(Map.of(), Map.of(), Map.of(), null, 1L));
  }
}
