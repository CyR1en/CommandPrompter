package dev.cyr1en.promptpaper.preset;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.cyr1en.promptcore.logic.transform.CompiledTemplate;
import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import org.junit.jupiter.api.Test;

class TrustedPresetActionTest {

  private final Gson gson = PresetGson.presetGson();

  @Test
  void validActionCreation() {
    CompiledTemplate template = TemplateCompiler.compile("give {player} diamond 1");
    TrustedPresetAction action = new TrustedPresetAction(template, ExecuteAs.CONSOLE, 20);

    assertEquals(template, action.command());
    assertEquals("give {player} diamond 1", action.command().source());
    assertEquals(ExecuteAs.CONSOLE, action.executeAs());
    assertEquals(20, action.delayTicks());
  }

  @Test
  void factoryMethodCompilesString() {
    TrustedPresetAction action =
        TrustedPresetAction.of("eco give {player} 100", ExecuteAs.PLAYER, 40);

    assertEquals("eco give {player} 100", action.command().source());
    assertEquals(ExecuteAs.PLAYER, action.executeAs());
    assertEquals(40, action.delayTicks());
  }

  @Test
  void factoryMethodDefaultZeroDelay() {
    TrustedPresetAction action = TrustedPresetAction.of("say hello", ExecuteAs.CONSOLE);
    assertEquals(0, action.delayTicks());
  }

  @Test
  void delayBoundsEnforced() {
    CompiledTemplate template = TemplateCompiler.compile("test");

    // Min (0) and Max (72000) allowed
    assertDoesNotThrow(() -> new TrustedPresetAction(template, ExecuteAs.CONSOLE, 0));
    assertDoesNotThrow(() -> new TrustedPresetAction(template, ExecuteAs.CONSOLE, 72000));

    // Negative and > 72000 rejected
    assertThrows(
        IllegalArgumentException.class,
        () -> new TrustedPresetAction(template, ExecuteAs.CONSOLE, -1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TrustedPresetAction(template, ExecuteAs.CONSOLE, 72001));
  }

  @Test
  void nullParametersRejected() {
    CompiledTemplate template = TemplateCompiler.compile("test");
    assertThrows(
        NullPointerException.class, () -> new TrustedPresetAction(null, ExecuteAs.CONSOLE, 0));
    assertThrows(NullPointerException.class, () -> new TrustedPresetAction(template, null, 0));
  }

  @Test
  void commandLengthLimitEnforced() {
    String longCmd = "a".repeat(1025);
    assertThrows(
        IllegalArgumentException.class,
        () -> TrustedPresetAction.of(longCmd, ExecuteAs.CONSOLE, 0));
  }

  @Test
  void c0ControlCharactersRejected() {
    String badCmd = "say hello\u0000world";
    assertThrows(
        IllegalArgumentException.class,
        () -> TrustedPresetAction.of(badCmd, ExecuteAs.CONSOLE, 0));
  }

  @Test
  void blankCommandRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> TrustedPresetAction.of("   ", ExecuteAs.CONSOLE, 0));
  }

  @Test
  void jsonDeserializeStructuredObject() {
    String json =
        """
        {
          "command": "give {player} diamond {0}",
          "execute_as": "console",
          "delay_ticks": 10
        }
        """;
    TrustedPresetAction action = gson.fromJson(json, TrustedPresetAction.class);
    assertNotNull(action);
    assertEquals("give {player} diamond {0}", action.command().source());
    assertEquals(ExecuteAs.CONSOLE, action.executeAs());
    assertEquals(10, action.delayTicks());
  }

  @Test
  void jsonDeserializeDefaultsDelayToZero() {
    String json =
        """
        {
          "command": "say {player} joined",
          "execute_as": "player"
        }
        """;
    TrustedPresetAction action = gson.fromJson(json, TrustedPresetAction.class);
    assertNotNull(action);
    assertEquals("say {player} joined", action.command().source());
    assertEquals(ExecuteAs.PLAYER, action.executeAs());
    assertEquals(0, action.delayTicks());
  }

  @Test
  void jsonDeserializeStringWithConsolePrefix() {
    String json = "\"console: eco give {player} 50\"";
    TrustedPresetAction action = gson.fromJson(json, TrustedPresetAction.class);
    assertNotNull(action);
    assertEquals("eco give {player} 50", action.command().source());
    assertEquals(ExecuteAs.CONSOLE, action.executeAs());
    assertEquals(0, action.delayTicks());
  }

  @Test
  void jsonDeserializeStringWithPlayerPrefix() {
    String json = "\"player: spawn\"";
    TrustedPresetAction action = gson.fromJson(json, TrustedPresetAction.class);
    assertNotNull(action);
    assertEquals("spawn", action.command().source());
    assertEquals(ExecuteAs.PLAYER, action.executeAs());
    assertEquals(0, action.delayTicks());
  }

  @Test
  void jsonDeserializeMissingRequiredFieldsThrows() {
    String noCmd = "{\"execute_as\": \"console\"}";
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(noCmd, TrustedPresetAction.class));

    String noExec = "{\"command\": \"say hi\"}";
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(noExec, TrustedPresetAction.class));
  }

  @Test
  void jsonRoundTrip() {
    TrustedPresetAction original =
        TrustedPresetAction.of("broadcast {player} won", ExecuteAs.CONSOLE, 100);
    String json = gson.toJson(original);
    TrustedPresetAction deserialized = gson.fromJson(json, TrustedPresetAction.class);

    assertEquals(original.command().source(), deserialized.command().source());
    assertEquals(original.executeAs(), deserialized.executeAs());
    assertEquals(original.delayTicks(), deserialized.delayTicks());
  }
}
