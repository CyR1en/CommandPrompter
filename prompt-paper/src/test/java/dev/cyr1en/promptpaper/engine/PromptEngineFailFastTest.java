package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.preset.CancelBehavior;
import dev.cyr1en.promptpaper.preset.ChatPrompt;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.ExecutionPolicy;
import dev.cyr1en.promptpaper.preset.PostCommand;
import dev.cyr1en.promptpaper.preset.PresetRegistry;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Scope 4 fail-fast coverage for {@link PromptEngine#intercept}. Verifies that
 * commands referencing unknown preset ids are rejected with a localized error
 * message and do not start a session, while commands referencing known ids
 * (or no presets at all) behave exactly as before.
 */
class PromptEngineFailFastTest extends MockBukkitTest {

  private PromptEngine engine;
  private PresetRegistry registry;

  @BeforeEach
  void setUp() {
    registry = mock(PresetRegistry.class);
    when(plugin.getPresetRegistry()).thenReturn(registry);
    engine = new PromptEngine(plugin, scheduler);
  }

  // --- happy paths ---

  @Test
  void commandWithoutPresetTagsStillStartsSession() {
    var result = engine.intercept(createPlayer(), "/cmd <a:why> please");
    assertTrue(result.isPresent());
    assertEquals(1, result.get().promptTags().size());
    assertFalse(result.get().promptTags().get(0).isPreset());
  }

  @Test
  void commandWithKnownPresetPromptStartsSession() {
    var chat = new ChatPrompt("chat", "my_id", "Why?", new CancelBehavior(false, "", false, ""), true);
    when(registry.getPrompt("my_id")).thenReturn(Optional.of(chat));

    var result = engine.intercept(createPlayer(), "/cmd <@my_id>");
    assertTrue(result.isPresent());
    assertEquals(1, result.get().promptTags().size());
    assertTrue(result.get().promptTags().get(0).isPreset());
    assertEquals("my_id", result.get().promptTags().get(0).displayText());
  }

  @Test
  void commandWithKnownPresetPostCommandOnlyDoesNotStartSession() {
    // A command with only a preset post-command (no prompt tags) does not
    // start a session — but the engine must still accept the preset id
    // (no fail-fast). The listener is responsible for cancelling the
    // PlayerCommandPreprocessEvent to keep the literal <!@id> markup out
    // of the dispatcher.
    var pc = new PostCommand("log_id", "say {player} hi", ExecutionPolicy.ON_COMPLETE, ExecuteAs.CONSOLE, 0);
    when(registry.getPostCommand("log_id")).thenReturn(Optional.of(pc));

    var result = engine.intercept(createPlayer(), "/cmd <!@log_id>");
    assertTrue(result.isEmpty(), "no prompt tags → no session");
  }

  @Test
  void commandWithKnownPresetPromptAndPresetPostCommandStartsSession() {
    var chat = new ChatPrompt("chat", "my_id", "Why?", new CancelBehavior(false, "", false, ""), true);
    var pc = new PostCommand("log_id", "log {player}", ExecutionPolicy.ON_COMPLETE, ExecuteAs.CONSOLE, 0);
    when(registry.getPrompt("my_id")).thenReturn(Optional.of(chat));
    when(registry.getPostCommand("log_id")).thenReturn(Optional.of(pc));

    var result = engine.intercept(createPlayer(), "/cmd <@my_id> <!@log_id>");
    assertTrue(result.isPresent());
    assertEquals(1, result.get().promptTags().size());
    assertTrue(result.get().promptTags().get(0).isPreset());
    assertEquals(1, result.get().postCmds().size());
    assertTrue(result.get().postCmds().get(0).isPreset());
  }

  // --- fail-fast paths ---

  @Test
  void missingPromptPresetFailsFast() {
    when(registry.getPrompt("nope")).thenReturn(Optional.empty());
    var player = createPlayer();

    var result = engine.intercept(player, "/cmd <@nope>");

    assertTrue(result.isEmpty(), "missing preset must not start a session");
    assertFalse(engine.hasActiveSession(player));
  }

  @Test
  void missingPostCommandPresetFailsFast() {
    when(registry.getPostCommand("nope")).thenReturn(Optional.empty());
    var player = createPlayer();

    var result = engine.intercept(player, "/cmd <!@nope>");

    assertTrue(result.isEmpty());
    assertFalse(engine.hasActiveSession(player));
  }

  @Test
  void mixedKnownAndMissingPresetsFailsFast() {
    var chat = new ChatPrompt("chat", "known", "x", new CancelBehavior(false, "", false, ""), true);
    when(registry.getPrompt("known")).thenReturn(Optional.of(chat));
    when(registry.getPrompt("missing")).thenReturn(Optional.empty());
    var player = createPlayer();

    var result = engine.intercept(player, "/cmd <@known> <@missing>");

    assertTrue(result.isEmpty(), "any missing preset aborts the whole command");
    assertFalse(engine.hasActiveSession(player));
  }

  @Test
  void missingPresetsAreReportedToPlayer() {
    when(registry.getPrompt("nope")).thenReturn(Optional.empty());
    var i18n = plugin.getConfigLoader().getI18n();
    when(i18n.get(anyString())).thenReturn(Component.text("missing preset error"));

    var player = createPlayer();
    engine.intercept(player, "/cmd <@nope>");

    // The engine must send a localized message; the exact key is
    // command.error.missing_preset, but the value is what reaches the player.
    verify(i18n, times(1)).get("command.error.missing_preset");
  }

  @Test
  void noRegistryTreatedAsAllPresetsMissing() {
    // Belt-and-suspenders: if the registry is null (e.g. early startup),
    // the engine treats every preset ref as missing so the player never
    // sees a silent pass for a half-loaded config.
    when(plugin.getPresetRegistry()).thenReturn(null);
    engine = new PromptEngine(plugin, scheduler);
    var i18n = plugin.getConfigLoader().getI18n();
    when(i18n.get(anyString())).thenReturn(Component.text("missing preset error"));

    var player = createPlayer();
    var result = engine.intercept(player, "/cmd <@any_id>");

    assertTrue(result.isEmpty());
    verify(i18n, times(1)).get("command.error.missing_preset");
  }

  // --- commandHasTagForm helper ---

  @Test
  void commandHasTagFormDetectsPresetTag() {
    assertTrue(engine.commandHasTagForm("/cmd <@my_prompt>"));
  }

  @Test
  void commandHasTagFormDetectsInlineTag() {
    assertTrue(engine.commandHasTagForm("/cmd <a:why>"));
  }

  @Test
  void commandHasTagFormFalseForPlainCommand() {
    assertFalse(engine.commandHasTagForm("/cmd no tags"));
  }

  @Test
  void commandHasTagFormFalseForNullOrBlank() {
    assertFalse(engine.commandHasTagForm(null));
    assertFalse(engine.commandHasTagForm(""));
    assertFalse(engine.commandHasTagForm("   "));
  }

  // --- hasPresetReferences() ---

  @Test
  void hasPresetReferencesTrueForPromptPreset() {
    assertTrue(engine.hasPresetReferences("/cmd <@my_prompt>"));
  }

  @Test
  void hasPresetReferencesTrueForPostCommandPreset() {
    assertTrue(engine.hasPresetReferences("/cmd <!@log_id>"));
  }

  @Test
  void hasPresetReferencesFalseForLegacyTags() {
    assertFalse(engine.hasPresetReferences("/cmd <a:why>"));
    assertFalse(engine.hasPresetReferences("/cmd <!log>"));
  }

  @Test
  void hasPresetReferencesFalseForPlainCommand() {
    assertFalse(engine.hasPresetReferences("/cmd no tags"));
    assertFalse(engine.hasPresetReferences(null));
  }

  // --- regression: pre-Scope-4 behaviour preserved ---

  @Test
  void legacyPostCommandsDoNotStartSessionOrFailFast() {
    // A legacy inline PCM (<!cmd>) is not a preset — it must not be
    // cross-checked against the registry. Even with a null registry the
    // engine returns empty (no session, no fail-fast error) and the
    // command passes through to the dispatcher unchanged.
    when(plugin.getPresetRegistry()).thenReturn(null);
    engine = new PromptEngine(plugin, scheduler);
    var player = createPlayer();

    var result = engine.intercept(player, "/cmd <!ban {0}>");
    assertTrue(result.isEmpty(), "no prompt tags → no session");
    assertFalse(engine.hasActiveSession(player));
  }
}
