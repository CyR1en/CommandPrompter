package dev.cyr1en.promptcore.parser;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.ParsedCommand;
import dev.cyr1en.promptcore.plan.PreDispatchGateSpec;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PreDispatchGateParserTest {

  private final CommandLineParser parser = new CommandLineParser();

  @Test
  void testValidSingleGate() {
    var result = parser.parse("/pay Steve 100 <!gate:@approval_preset>");

    assertTrue(result.hasGates());
    assertEquals(1, result.gateCount());
    assertEquals(1, result.preDispatchGates().size());

    PreDispatchGateSpec spec = result.preDispatchGates().get(0);
    assertInstanceOf(PreDispatchGateSpec.Approval.class, spec);
    assertEquals("approval_preset", ((PreDispatchGateSpec.Approval) spec).presetId());

    assertEquals(1, result.templateSpans().size());
    var span = result.templateSpans().get(0);
    assertTrue(span.isGate());
    assertFalse(span.pcm());
    assertEquals("<!gate:@approval_preset>", span.rawText());
    assertEquals(0, span.parsedIndex());

    assertEquals("/pay Steve 100 ", ParsedCommand.buildPartialCommand(result, List.of()));
  }

  @Test
  void testValidGateOrdering() {
    var result = parser.parse("/pay Steve 100 <!gate:@first> <!gate:@second> <!gate:@third>");

    assertEquals(3, result.gateCount());
    assertEquals(
        List.of(
            new PreDispatchGateSpec.Approval("first"),
            new PreDispatchGateSpec.Approval("second"),
            new PreDispatchGateSpec.Approval("third")),
        result.preDispatchGates());

    assertEquals("/pay Steve 100 ", ParsedCommand.buildPartialCommand(result, List.of()));
  }

  @Test
  void testValidMixedPromptsPcmsAndGates() {
    var result =
        parser.parse(
            "/cmd <a:prompt1><! pcm1><!gate:@gate1> <a:prompt2><!gate:@gate2><!! pcm2><!@preset_pcm>");

    assertEquals(2, result.promptCount());
    assertEquals(3, result.pcmCount());
    assertEquals(2, result.gateCount());

    assertEquals(
        List.of(
            new PreDispatchGateSpec.Approval("gate1"), new PreDispatchGateSpec.Approval("gate2")),
        result.preDispatchGates());

    assertEquals(
        List.of("prompt1", "prompt2"),
        result.promptTags().stream().map(t -> t.displayText()).toList());

    assertEquals("pcm1", result.postCmds().get(0).command());
    assertFalse(result.postCmds().get(0).onCancel());

    assertEquals("pcm2", result.postCmds().get(1).command());
    assertTrue(result.postCmds().get(1).onCancel());

    assertEquals("preset_pcm", result.postCmds().get(2).command());
    assertTrue(result.postCmds().get(2).isPreset());

    assertEquals(7, result.templateSpans().size());

    var partial = ParsedCommand.buildPartialCommand(result, List.of("ans1", "ans2"));
    assertEquals("/cmd \"ans1\" \"ans2\" ", partial);
  }

  @Test
  void testValidGateWithValidIdCharacters() {
    var result = parser.parse("/test <!gate:@valid-gate.1_v2>");

    assertEquals(1, result.gateCount());
    assertEquals(
        "valid-gate.1_v2",
        ((PreDispatchGateSpec.Approval) result.preDispatchGates().get(0)).presetId());
  }

  @Test
  void testDuplicateGateIdRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/pay Steve 100 <!gate:@trade_gate> <!gate:@trade_gate>"));
  }

  @Test
  void testMaxGateCapEnforced() {
    // 16 gates succeeds
    String sixteenGates =
        IntStream.range(0, 16)
            .mapToObj(i -> "<!gate:@gate_" + i + ">")
            .collect(Collectors.joining(" ", "/cmd ", ""));
    var result = parser.parse(sixteenGates);
    assertEquals(16, result.gateCount());

    // 17 gates throws
    String seventeenGates =
        IntStream.range(0, 17)
            .mapToObj(i -> "<!gate:@gate_" + i + ">")
            .collect(Collectors.joining(" ", "/cmd ", ""));
    assertThrows(IllegalArgumentException.class, () -> parser.parse(seventeenGates));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/cmd <!gate:@>",
        "/cmd <!gate:@   >",
        "/cmd <!gate:@AdminGate>",
        "/cmd <!gate:@UPPERCASE>",
        "/cmd <!gate:@MixedCase>",
        "/cmd <!gate:@id_with_space foo>",
        "/cmd <!gate:@id; /op attacker>",
        "/cmd <!gate:@id\n/op attacker>",
        "/cmd <!gate:@id{0}>",
        "/cmd <!gate:@id %player_name%>",
        "/cmd <!gate:@id @console>",
        "/cmd <!gate:@id @player>",
        "/cmd <!@console gate:@id>",
        "/cmd <!@player gate:@id>",
        "/cmd <!gate:approval>",
        "/cmd <!gate:approval target=player>",
        "/cmd <!gate:foo>",
        "/cmd <!!gate:@id>",
        "/cmd <!!gate:foo>",
        "/cmd <!:20gate:@id>",
        "/cmd <!:20 gate:@id>",
        "/cmd <!gate:@id:20>",
        "/cmd <!gate:@id delay=20>",
        "/cmd <! gate:@id>",
        "/cmd <!gate: @id>",
        "/cmd <!gate :@id>",
        "/cmd <!gate:@invalid$char>",
        "/cmd <!gate:@invalid/path>",
        "/cmd <!gate:@invalid\\backslash>"
      })
  void testMalformedGateVariantsRejected(String input) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse(input));
  }

  @Test
  void testOversizedGateIdRejected() {
    String oversizedId = "a".repeat(65);
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse("/cmd <!gate:@" + oversizedId + ">"));
  }

  @Test
  void testNoCollisionWithExistingSyntax() {
    // PCM Preset
    var pcmPreset = parser.parse("/cmd <!@my_preset>");
    assertEquals(0, pcmPreset.gateCount());
    assertEquals(1, pcmPreset.pcmCount());
    assertTrue(pcmPreset.postCmds().get(0).isPreset());
    assertEquals("my_preset", pcmPreset.postCmds().get(0).command());

    // PCM Preset with name starting with gate
    var pcmGateNamed = parser.parse("/cmd <!@gate_something>");
    assertEquals(0, pcmGateNamed.gateCount());
    assertEquals(1, pcmGateNamed.pcmCount());
    assertTrue(pcmGateNamed.postCmds().get(0).isPreset());
    assertEquals("gate_something", pcmGateNamed.postCmds().get(0).command());

    // Prompt Preset
    var promptPreset = parser.parse("/cmd <@dialog_preset>");
    assertEquals(0, promptPreset.gateCount());
    assertEquals(1, promptPreset.promptCount());
    assertTrue(promptPreset.promptTags().get(0).isPreset());
    assertEquals("dialog_preset", promptPreset.promptTags().get(0).displayText());

    // Normal PCM commands
    var pcmNormal = parser.parse("/cmd <!say hello>");
    assertEquals(0, pcmNormal.gateCount());
    assertEquals(1, pcmNormal.pcmCount());
    assertEquals("say hello", pcmNormal.postCmds().get(0).command());

    var pcmGateCmd = parser.parse("/cmd <!gate open>");
    assertEquals(0, pcmGateCmd.gateCount());
    assertEquals(1, pcmGateCmd.pcmCount());
    assertEquals("gate open", pcmGateCmd.postCmds().get(0).command());

    var pcmGatekeeper = parser.parse("/cmd <!gatekeeper open>");
    assertEquals(0, pcmGatekeeper.gateCount());
    assertEquals(1, pcmGatekeeper.pcmCount());
    assertEquals("gatekeeper open", pcmGatekeeper.postCmds().get(0).command());
  }

  @Test
  void testEscapedGateTagNotParsed() {
    var parsed = parser.parse("/pay Steve 100 \\<!gate:@my_gate\\>");
    assertEquals(0, parsed.gateCount());
    assertEquals(0, parsed.promptCount());
    assertEquals(0, parsed.pcmCount());
    assertEquals(
        "/pay Steve 100 <!gate:@my_gate> ", ParsedCommand.buildPartialCommand(parsed, List.of()));
  }
}
