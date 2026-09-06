package dev.cyr1en.promptpaper.preset;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.cyr1en.promptcore.logic.transform.CompiledTemplate;
import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import dev.cyr1en.promptcore.logic.transform.TransformException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApprovalGateDefinitionTest {

  private final Gson gson = PresetGson.presetGson();

  @Test
  void validCreationFull() {
    CompiledTemplate target = TemplateCompiler.compile("admin.permission");
    CompiledTemplate message = TemplateCompiler.compile("<red>{player} requested action</red>");
    TrustedPresetAction onDeny =
        TrustedPresetAction.of("msg {player} Request denied", ExecuteAs.CONSOLE, 0);

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate_1", target, message, 60, SelfApprovalPolicy.REQUIRE_CONFIRM, onDeny);

    assertEquals("gate_1", gate.id());
    assertEquals(target, gate.target());
    assertEquals(message, gate.message());
    assertEquals(60, gate.timeout());
    assertEquals(SelfApprovalPolicy.REQUIRE_CONFIRM, gate.selfApprovalPolicy());
    assertEquals(onDeny, gate.onDenyAction());
  }

  @Test
  void validCreationDefaults() {
    CompiledTemplate target = TemplateCompiler.compile("staff");
    CompiledTemplate message = TemplateCompiler.compile("Confirm?");

    ApprovalGateDefinition gate = new ApprovalGateDefinition("gate_default", target, message);

    assertEquals("gate_default", gate.id());
    assertEquals(30, gate.timeout());
    assertEquals(SelfApprovalPolicy.AUTO_APPROVE, gate.selfApprovalPolicy());
    assertNull(gate.onDenyAction());
  }

  @ParameterizedTest
  @ValueSource(strings = {"gate", "gate-1", "gate.sub", "gate_2", "a", "123", "a.b-c_d"})
  void validIdsAccepted(String validId) {
    CompiledTemplate t = TemplateCompiler.compile("target");
    CompiledTemplate m = TemplateCompiler.compile("message");
    assertDoesNotThrow(() -> new ApprovalGateDefinition(validId, t, m));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "Gate",
        "GATE_1",
        "gate 1",
        "gate@admin",
        "gate/sub",
        "gate:name",
        "gate!",
        "gate#1"
      })
  void invalidIdsRejected(String invalidId) {
    CompiledTemplate t = TemplateCompiler.compile("target");
    CompiledTemplate m = TemplateCompiler.compile("message");
    assertThrows(IllegalArgumentException.class, () -> new ApprovalGateDefinition(invalidId, t, m));
  }

  @Test
  void idOver64CharsRejected() {
    String longId = "a".repeat(65);
    CompiledTemplate t = TemplateCompiler.compile("target");
    CompiledTemplate m = TemplateCompiler.compile("message");
    assertThrows(IllegalArgumentException.class, () -> new ApprovalGateDefinition(longId, t, m));
  }

  @Test
  void timeoutBoundsEnforced() {
    CompiledTemplate t = TemplateCompiler.compile("target");
    CompiledTemplate m = TemplateCompiler.compile("message");

    // Min 1 and Max 3600 allowed
    assertDoesNotThrow(
        () ->
            new ApprovalGateDefinition("gate_min", t, m, 1, SelfApprovalPolicy.AUTO_APPROVE, null));
    assertDoesNotThrow(
        () ->
            new ApprovalGateDefinition(
                "gate_max", t, m, 3600, SelfApprovalPolicy.AUTO_APPROVE, null));

    // 0, negative, and 3601 rejected
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ApprovalGateDefinition(
                "gate_zero", t, m, 0, SelfApprovalPolicy.AUTO_APPROVE, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ApprovalGateDefinition(
                "gate_neg", t, m, -10, SelfApprovalPolicy.AUTO_APPROVE, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ApprovalGateDefinition(
                "gate_over", t, m, 3601, SelfApprovalPolicy.AUTO_APPROVE, null));
  }

  @Test
  void onDenyActionMustHaveZeroDelay() {
    CompiledTemplate t = TemplateCompiler.compile("target");
    CompiledTemplate m = TemplateCompiler.compile("message");
    TrustedPresetAction nonZeroDelay = TrustedPresetAction.of("say denied", ExecuteAs.CONSOLE, 10);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ApprovalGateDefinition(
                "gate_delay", t, m, 30, SelfApprovalPolicy.AUTO_APPROVE, nonZeroDelay));
  }

  @Test
  void templateLengthBoundsEnforced() {
    String longStr = "a".repeat(1025);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            ApprovalGateDefinition.of(
                "gate_long", longStr, "msg", 30, SelfApprovalPolicy.AUTO_APPROVE, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ApprovalGateDefinition.of(
                "gate_long", "target", longStr, 30, SelfApprovalPolicy.AUTO_APPROVE, null));
    assertThrows(TransformException.class, () -> TemplateCompiler.compile(longStr));
  }

  @Test
  void templateControlCharactersRejectedAtCompileTime() {
    assertThrows(TransformException.class, () -> TemplateCompiler.compile("target\u0000bad"));
    assertThrows(TransformException.class, () -> TemplateCompiler.compile("msg\u0007bad"));
    assertThrows(TransformException.class, () -> TemplateCompiler.compile("target\tname"));
    assertThrows(TransformException.class, () -> TemplateCompiler.compile("target\nname"));
    assertThrows(TransformException.class, () -> TemplateCompiler.compile("target\r\nname"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\u2000", "\u3000"})
  void directConstructionRejectsBlankTargetTemplate(String blankTarget) {
    CompiledTemplate compiledBlank = TemplateCompiler.compile(blankTarget);
    CompiledTemplate validMsg = TemplateCompiler.compile("Valid Message");

    assertThrows(
        IllegalArgumentException.class,
        () -> new ApprovalGateDefinition("gate_blank", compiledBlank, validMsg));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\u2000", "\u3000"})
  void directConstructionRejectsBlankMessageTemplate(String blankMessage) {
    CompiledTemplate validTarget = TemplateCompiler.compile("ValidTarget");
    CompiledTemplate compiledBlank = TemplateCompiler.compile(blankMessage);

    assertThrows(
        IllegalArgumentException.class,
        () -> new ApprovalGateDefinition("gate_blank", validTarget, compiledBlank));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t", "\n", "\r\n", "\u2000", "\u3000", "  \t  "})
  void factoryMethodRejectsBlankTarget(String blankTarget) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ApprovalGateDefinition.of(
                "gate_blank",
                blankTarget,
                "Valid Message",
                30,
                SelfApprovalPolicy.AUTO_APPROVE,
                null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t", "\n", "\r\n", "\u2000", "\u3000", "  \t  "})
  void factoryMethodRejectsBlankMessage(String blankMessage) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ApprovalGateDefinition.of(
                "gate_blank",
                "ValidTarget",
                blankMessage,
                30,
                SelfApprovalPolicy.AUTO_APPROVE,
                null));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\u0000",
        "\u001F",
        "\u007F",
        "target\tname",
        "target\nname",
        "target\r\nname",
        "target\u0000bad",
        "msg\u0007bad"
      })
  void factoryMethodRejectsControlCharacters(String controlStr) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ApprovalGateDefinition.of(
                "gate_ctrl", controlStr, "valid", 30, SelfApprovalPolicy.AUTO_APPROVE, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ApprovalGateDefinition.of(
                "gate_ctrl", "valid", controlStr, 30, SelfApprovalPolicy.AUTO_APPROVE, null));
  }

  @Test
  void directConstructionPreservesValidInternalWhitespace() {
    String target = "permission.node with space";
    String message = "<yellow>Player {player} requested approval for: {command}</yellow>";

    ApprovalGateDefinition gate =
        ApprovalGateDefinition.of(
            "gate_spaces", target, message, 30, SelfApprovalPolicy.AUTO_APPROVE, null);

    assertEquals(target, gate.target().source());
    assertEquals(message, gate.message().source());
  }

  @Test
  void jsonDeserializeFull() {
    String json =
        """
        {
          "id": "admin_gate",
          "target": "commandprompter.admin",
          "message": "<yellow>Player {player} requested: {command}</yellow>",
          "timeout": 45,
          "self_approval_policy": "require_confirm",
          "on_deny": {
            "command": "msg {player} Admin rejected your request",
            "execute_as": "console",
            "delay_ticks": 0
          }
        }
        """;
    ApprovalGateDefinition gate = gson.fromJson(json, ApprovalGateDefinition.class);
    assertNotNull(gate);
    assertEquals("admin_gate", gate.id());
    assertEquals("commandprompter.admin", gate.target().source());
    assertEquals("<yellow>Player {player} requested: {command}</yellow>", gate.message().source());
    assertEquals(45, gate.timeout());
    assertEquals(SelfApprovalPolicy.REQUIRE_CONFIRM, gate.selfApprovalPolicy());
    assertNotNull(gate.onDenyAction());
    assertEquals(
        "msg {player} Admin rejected your request", gate.onDenyAction().command().source());
    assertEquals(ExecuteAs.CONSOLE, gate.onDenyAction().executeAs());
    assertEquals(0, gate.onDenyAction().delayTicks());
  }

  @Test
  void jsonDeserializeMinimalDefaults() {
    String json =
        """
        {
          "id": "simple_gate",
          "target": "owner",
          "message": "Approve please"
        }
        """;
    ApprovalGateDefinition gate = gson.fromJson(json, ApprovalGateDefinition.class);
    assertNotNull(gate);
    assertEquals("simple_gate", gate.id());
    assertEquals("owner", gate.target().source());
    assertEquals("Approve please", gate.message().source());
    assertEquals(30, gate.timeout());
    assertEquals(SelfApprovalPolicy.AUTO_APPROVE, gate.selfApprovalPolicy());
    assertNull(gate.onDenyAction());
  }

  @Test
  void jsonDeserializeRejectsInvalidId() {
    String json =
        """
        {
          "id": "INVALID ID",
          "target": "admin",
          "message": "msg"
        }
        """;
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(json, ApprovalGateDefinition.class));
  }

  @Test
  void jsonDeserializeMissingRequiredFieldsThrows() {
    String noId = "{\"target\": \"admin\", \"message\": \"msg\"}";
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(noId, ApprovalGateDefinition.class));

    String noTarget = "{\"id\": \"gate_1\", \"message\": \"msg\"}";
    assertThrows(
        IllegalArgumentException.class,
        () -> gson.fromJson(noTarget, ApprovalGateDefinition.class));

    String noMsg = "{\"id\": \"gate_1\", \"target\": \"admin\"}";
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(noMsg, ApprovalGateDefinition.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t", "\n", "\r\n", "\u2000", "\u3000", "  \t  "})
  void jsonDeserializeRejectsBlankTarget(String blankTarget) {
    String json =
        String.format(
            """
            {
              "id": "gate_1",
              "target": "%s",
              "message": "Valid Message"
            }
            """,
            escapeJsonString(blankTarget));
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(json, ApprovalGateDefinition.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t", "\n", "\r\n", "\u2000", "\u3000", "  \t  "})
  void jsonDeserializeRejectsBlankMessage(String blankMessage) {
    String json =
        String.format(
            """
            {
              "id": "gate_1",
              "target": "valid_target",
              "message": "%s"
            }
            """,
            escapeJsonString(blankMessage));
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(json, ApprovalGateDefinition.class));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\u0000",
        "\u001F",
        "\u007F",
        "target\tname",
        "target\nname",
        "target\r\nname",
        "target\u0000bad",
        "msg\u0007bad"
      })
  void jsonDeserializeRejectsControlCharacters(String controlStr) {
    String jsonTarget =
        String.format(
            """
            {
              "id": "gate_1",
              "target": "%s",
              "message": "Valid Message"
            }
            """,
            escapeJsonString(controlStr));
    assertThrows(
        IllegalArgumentException.class,
        () -> gson.fromJson(jsonTarget, ApprovalGateDefinition.class));

    String jsonMsg =
        String.format(
            """
            {
              "id": "gate_1",
              "target": "valid_target",
              "message": "%s"
            }
            """,
            escapeJsonString(controlStr));
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(jsonMsg, ApprovalGateDefinition.class));
  }

  @Test
  void jsonDeserializePreservesValidInternalWhitespace() {
    String json =
        """
        {
          "id": "gate_ws",
          "target": "admin permission node",
          "message": "<green>Please confirm action for {player}</green>"
        }
        """;
    ApprovalGateDefinition gate = gson.fromJson(json, ApprovalGateDefinition.class);
    assertNotNull(gate);
    assertEquals("gate_ws", gate.id());
    assertEquals("admin permission node", gate.target().source());
    assertEquals("<green>Please confirm action for {player}</green>", gate.message().source());
  }

  private static String escapeJsonString(String raw) {
    StringBuilder sb = new StringBuilder();
    for (char c : raw.toCharArray()) {
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20 || c == 0x7F) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }

  @Test
  void jsonRoundTrip() {
    ApprovalGateDefinition original =
        ApprovalGateDefinition.of(
            "test_gate",
            "admin.perm",
            "Please approve",
            50,
            SelfApprovalPolicy.REQUIRE_CONFIRM,
            TrustedPresetAction.of("say denied", ExecuteAs.CONSOLE, 0));

    String json = gson.toJson(original);
    ApprovalGateDefinition deserialized = gson.fromJson(json, ApprovalGateDefinition.class);

    assertEquals(original.id(), deserialized.id());
    assertEquals(original.target().source(), deserialized.target().source());
    assertEquals(original.message().source(), deserialized.message().source());
    assertEquals(original.timeout(), deserialized.timeout());
    assertEquals(original.selfApprovalPolicy(), deserialized.selfApprovalPolicy());
    assertNotNull(deserialized.onDenyAction());
    assertEquals(
        original.onDenyAction().command().source(), deserialized.onDenyAction().command().source());
  }
}
