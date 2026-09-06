package dev.cyr1en.promptpaper.preset;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.cyr1en.promptcore.logic.condition.Condition;
import dev.cyr1en.promptcore.logic.condition.ConditionCompileOptions;
import dev.cyr1en.promptcore.logic.condition.ConditionCompiler;
import dev.cyr1en.promptcore.logic.condition.ConditionDepthException;
import dev.cyr1en.promptcore.logic.condition.ConditionParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConditionalPostCommandDefinitionTest {

  private final Gson gson = PresetGson.presetGson();

  @Test
  void validCreationWithPapiCondition() {
    TrustedPresetAction ifTrue =
        TrustedPresetAction.of("give {player} diamond 5", ExecuteAs.CONSOLE, 0);
    TrustedPresetAction ifFalse =
        TrustedPresetAction.of("msg {player} Not enough money", ExecuteAs.CONSOLE, 0);

    ConditionalPostCommandDefinition def =
        ConditionalPostCommandDefinition.compile(
            "check_bal",
            "%vault_eco_balance% >= 100",
            ExecutionPolicy.ON_COMPLETE,
            ifTrue,
            ifFalse);

    assertEquals("check_bal", def.id());
    assertEquals("%vault_eco_balance% >= 100", def.condition().source());
    assertEquals(ExecutionPolicy.ON_COMPLETE, def.executionPolicy());
    assertEquals(ifTrue, def.ifTrueAction());
    assertEquals(ifFalse, def.ifFalseAction());
  }

  @Test
  void papiRefAllowedInPresetCompileOptionsOnly() {
    String papiCond = "%player_level% > 5";

    // Allowed in preset context
    assertDoesNotThrow(
        () -> ConditionCompiler.compile(papiCond, ConditionCompileOptions.forPreset()));

    // Disallowed in inline/untrusted context
    assertThrows(
        ConditionParseException.class,
        () -> ConditionCompiler.compile(papiCond, ConditionCompileOptions.forInline()));
  }

  @Test
  void singleBranchAllowed() {
    TrustedPresetAction ifTrue = TrustedPresetAction.of("say true", ExecuteAs.CONSOLE);
    TrustedPresetAction ifFalse = TrustedPresetAction.of("say false", ExecuteAs.CONSOLE);

    // If true only
    ConditionalPostCommandDefinition def1 =
        ConditionalPostCommandDefinition.compile(
            "true_only", "{0} == 1", ExecutionPolicy.ON_COMPLETE, ifTrue, null);
    assertNotNull(def1.ifTrueAction());
    assertNull(def1.ifFalseAction());

    // If false only
    ConditionalPostCommandDefinition def2 =
        ConditionalPostCommandDefinition.compile(
            "false_only", "{0} == 1", ExecutionPolicy.ON_COMPLETE, null, ifFalse);
    assertNull(def2.ifTrueAction());
    assertNotNull(def2.ifFalseAction());
  }

  @Test
  void bothBranchesAbsentThrows() {
    Condition cond = ConditionCompiler.compile("{0} == 1", ConditionCompileOptions.forPreset());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ConditionalPostCommandDefinition(
                "no_branches", cond, ExecutionPolicy.ON_COMPLETE, null, null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"cmd", "cmd-1", "cmd.post", "cmd_2", "a", "123", "a.b-c_d"})
  void validIdsAccepted(String validId) {
    Condition cond = ConditionCompiler.compile("{0} == 1", ConditionCompileOptions.forPreset());
    TrustedPresetAction action = TrustedPresetAction.of("say hi", ExecuteAs.CONSOLE);
    assertDoesNotThrow(
        () ->
            new ConditionalPostCommandDefinition(
                validId, cond, ExecutionPolicy.ON_COMPLETE, action, null));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "Cmd", "CMD_1", "cmd 1", "cmd@admin", "cmd/sub", "cmd:name", "cmd!", "cmd#1"})
  void invalidIdsRejected(String invalidId) {
    Condition cond = ConditionCompiler.compile("{0} == 1", ConditionCompileOptions.forPreset());
    TrustedPresetAction action = TrustedPresetAction.of("say hi", ExecuteAs.CONSOLE);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ConditionalPostCommandDefinition(
                invalidId, cond, ExecutionPolicy.ON_COMPLETE, action, null));
  }

  @Test
  void conditionLengthLimitEnforced() {
    String longCond = "%papi% == " + "1".repeat(1025);
    TrustedPresetAction action = TrustedPresetAction.of("say hi", ExecuteAs.CONSOLE);
    assertThrows(
        ConditionParseException.class,
        () ->
            ConditionalPostCommandDefinition.compile(
                "long_cond", longCond, ExecutionPolicy.ON_COMPLETE, action, null));
  }

  @Test
  void conditionAstDepthLimitEnforced() {
    // 11 levels of negation exceeds depth 10
    String deepCond = "!(!(!(!(!(!(!(!(!(!(!({0} == 1)))))))))))";
    TrustedPresetAction action = TrustedPresetAction.of("say hi", ExecuteAs.CONSOLE);
    assertThrows(
        ConditionDepthException.class,
        () ->
            ConditionalPostCommandDefinition.compile(
                "deep_cond", deepCond, ExecutionPolicy.ON_COMPLETE, action, null));
  }

  @Test
  void conditionControlCharactersRejected() {
    String badCond = "{0} == \u0000";
    TrustedPresetAction action = TrustedPresetAction.of("say hi", ExecuteAs.CONSOLE);
    assertThrows(
        ConditionParseException.class,
        () ->
            ConditionalPostCommandDefinition.compile(
                "ctrl_cond", badCond, ExecutionPolicy.ON_COMPLETE, action, null));
  }

  @Test
  void conditionMalformedSyntaxRejected() {
    String malformed = "{0} = 1"; // Single '=' is invalid assignment
    TrustedPresetAction action = TrustedPresetAction.of("say hi", ExecuteAs.CONSOLE);
    assertThrows(
        ConditionParseException.class,
        () ->
            ConditionalPostCommandDefinition.compile(
                "bad_syntax", malformed, ExecutionPolicy.ON_COMPLETE, action, null));
  }

  @Test
  void jsonDeserializeFull() {
    String json =
        """
        {
          "id": "reward_check",
          "condition": "%player_has_permission_vip% == \\"yes\\"",
          "execution_policy": "on_complete",
          "if_true": {
            "command": "give {player} diamond 10",
            "execute_as": "console",
            "delay_ticks": 20
          },
          "if_false": {
            "command": "give {player} iron_ingot 10",
            "execute_as": "console",
            "delay_ticks": 0
          }
        }
        """;
    ConditionalPostCommandDefinition def =
        gson.fromJson(json, ConditionalPostCommandDefinition.class);
    assertNotNull(def);
    assertEquals("reward_check", def.id());
    assertEquals("%player_has_permission_vip% == \"yes\"", def.condition().source());
    assertEquals(ExecutionPolicy.ON_COMPLETE, def.executionPolicy());
    assertNotNull(def.ifTrueAction());
    assertEquals("give {player} diamond 10", def.ifTrueAction().command().source());
    assertEquals(20, def.ifTrueAction().delayTicks());
    assertNotNull(def.ifFalseAction());
    assertEquals("give {player} iron_ingot 10", def.ifFalseAction().command().source());
  }

  @Test
  void jsonDeserializeWithAliasesThenElse() {
    String json =
        """
        {
          "id": "alias_check",
          "condition": "{0} == \\"yes\\"",
          "execution_policy": "on_cancel",
          "then": {
            "command": "say confirmed",
            "execute_as": "player"
          },
          "else": {
            "command": "say denied",
            "execute_as": "player"
          }
        }
        """;
    ConditionalPostCommandDefinition def =
        gson.fromJson(json, ConditionalPostCommandDefinition.class);
    assertNotNull(def);
    assertEquals("alias_check", def.id());
    assertEquals(ExecutionPolicy.ON_CANCEL, def.executionPolicy());
    assertNotNull(def.ifTrueAction());
    assertEquals("say confirmed", def.ifTrueAction().command().source());
    assertEquals(ExecuteAs.PLAYER, def.ifTrueAction().executeAs());
    assertNotNull(def.ifFalseAction());
    assertEquals("say denied", def.ifFalseAction().command().source());
  }

  @Test
  void jsonDeserializeRejectsMissingBranches() {
    String json =
        """
        {
          "id": "no_branch",
          "condition": "{0} == 1",
          "execution_policy": "on_complete"
        }
        """;
    assertThrows(
        IllegalArgumentException.class,
        () -> gson.fromJson(json, ConditionalPostCommandDefinition.class));
  }

  @Test
  void jsonRoundTrip() {
    ConditionalPostCommandDefinition original =
        ConditionalPostCommandDefinition.compile(
            "round_trip",
            "%vault_eco_balance% >= 50",
            ExecutionPolicy.ON_COMPLETE,
            TrustedPresetAction.of("eco take {player} 50", ExecuteAs.CONSOLE, 0),
            TrustedPresetAction.of("msg {player} Too poor", ExecuteAs.CONSOLE, 0));

    String json = gson.toJson(original);
    ConditionalPostCommandDefinition deserialized =
        gson.fromJson(json, ConditionalPostCommandDefinition.class);

    assertEquals(original.id(), deserialized.id());
    assertEquals(original.condition().source(), deserialized.condition().source());
    assertEquals(original.executionPolicy(), deserialized.executionPolicy());
    assertEquals(
        original.ifTrueAction().command().source(), deserialized.ifTrueAction().command().source());
    assertEquals(
        original.ifFalseAction().command().source(),
        deserialized.ifFalseAction().command().source());
  }
}
