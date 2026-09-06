package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptcore.ParsedCommand;
import dev.cyr1en.promptcore.ParserConfig;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.TitleConfig;
import dev.cyr1en.promptcore.logic.condition.ConditionCompileOptions;
import dev.cyr1en.promptcore.logic.condition.ConditionCompiler;
import dev.cyr1en.promptcore.parser.CommandLineParser;
import dev.cyr1en.promptcore.plan.PreDispatchGateSpec;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.preset.ApprovalGateDefinition;
import dev.cyr1en.promptpaper.preset.CancelBehavior;
import dev.cyr1en.promptpaper.preset.ChatPrompt;
import dev.cyr1en.promptpaper.preset.PresetRegistry;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Dedicated regression tests for {@link PromptEngine#applyPresetSanitize}.
 *
 * <p>Verifies that overlaying preset definitions does not discard any metadata on {@link PromptTag}
 * (such as custom flags and compiled breakIf conditions) or {@link ParsedCommand} (such as
 * pre-dispatch gate specifications and template spans).
 */
class PromptEnginePresetSanitizeRegressionTest extends MockBukkitTest {

  private PromptEngine engine;
  private PresetRegistry registry;

  @BeforeEach
  void setUp() {
    registry = mock(PresetRegistry.class);
    when(plugin.getPresetRegistry()).thenReturn(registry);
    engine = new PromptEngine(plugin, scheduler);
  }

  @Test
  @DisplayName("applyPresetSanitize preserves flags, breakIf, preDispatchGates, and templateSpans")
  void applyPresetSanitizePreservesAllTagAndCommandMetadata() {
    var breakCondition =
        ConditionCompiler.compile("{0} == \"cancel\"", ConditionCompileOptions.forPreset());
    var titleConfig = new TitleConfig("Title", "Subtitle", 60);
    var customFlags = Map.of("rarity", "epic", "theme", "dark");
    var originalTag =
        new PromptTag(
            "<@test_prompt>",
            "",
            null,
            "test_prompt",
            true,
            "req",
            PromptTag.AnswerType.STRING,
            List.of(),
            true,
            titleConfig,
            45,
            customFlags,
            breakCondition);

    var parsedBase =
        new CommandLineParser(ParserConfig.ANGLE_BRACKETS)
            .parse("/trade <@test_prompt> <!log> <!gate:@trade_gate>");
    var parsed =
        new ParsedCommand(
            parsedBase.templateCommand(),
            List.of(originalTag),
            parsedBase.postCmds(),
            parsedBase.preDispatchGates(),
            parsedBase.parserConfig(),
            parsedBase.rawTemplateCommand(),
            parsedBase.templateSpans());

    var promptDef =
        new ChatPrompt(
            "chat",
            "test_prompt",
            "Enter trade value:",
            new CancelBehavior(false, "", false, ""),
            false // authoritative sanitize = false
            );
    var snapshot =
        new PresetSnapshot(
            Map.of("test_prompt", promptDef),
            Map.of(),
            Map.of("trade_gate", mock(ApprovalGateDefinition.class)),
            Map.of(),
            1L);

    var result = engine.applyPresetSanitize(parsed, snapshot);

    // Verify ParsedCommand fields
    assertEquals(parsed.templateCommand(), result.templateCommand());
    assertEquals(parsed.rawTemplateCommand(), result.rawTemplateCommand());
    assertEquals(parsed.parserConfig(), result.parserConfig());
    assertEquals(parsed.postCmds(), result.postCmds());
    assertEquals(parsed.templateSpans(), result.templateSpans());
    assertEquals(1, result.preDispatchGates().size(), "preDispatchGates must be preserved");
    assertEquals(parsed.preDispatchGates().get(0), result.preDispatchGates().get(0));

    // Verify PromptTag fields
    assertEquals(1, result.promptTags().size());
    var adjustedTag = result.promptTags().get(0);
    assertEquals(originalTag.rawTag(), adjustedTag.rawTag());
    assertEquals(originalTag.key(), adjustedTag.key());
    assertEquals(originalTag.filter(), adjustedTag.filter());
    assertEquals(originalTag.displayText(), adjustedTag.displayText());
    assertFalse(
        adjustedTag.sanitize(), "Authoritative preset sanitize=false must override parsed default");
    assertEquals(originalTag.validatorAlias(), adjustedTag.validatorAlias());
    assertEquals(originalTag.type(), adjustedTag.type());
    assertEquals(originalTag.subTags(), adjustedTag.subTags());
    assertTrue(adjustedTag.preset());
    assertEquals(originalTag.title(), adjustedTag.title());
    assertEquals(originalTag.timeout(), adjustedTag.timeout());
    assertEquals(customFlags, adjustedTag.flags(), "Custom flags map must be preserved");
    assertSame(breakCondition, adjustedTag.breakIf(), "breakIf condition must be preserved");
  }

  @Test
  @DisplayName("intercept with preset prompt and gate retains metadata and gates in session")
  void interceptPreservesMetadataEndToEnd() {
    var chatPrompt =
        new ChatPrompt(
            "chat",
            "confirm_preset",
            "Are you sure?",
            new CancelBehavior(false, "", false, ""),
            false);
    var gateDef = mock(ApprovalGateDefinition.class);
    when(gateDef.id()).thenReturn("admin_approval");

    var snapshot =
        new PresetSnapshot(
            Map.of("confirm_preset", chatPrompt),
            Map.of(),
            Map.of("admin_approval", gateDef),
            Map.of(),
            1L);
    when(registry.getSnapshot()).thenReturn(snapshot);
    when(registry.getPrompt("confirm_preset")).thenReturn(Optional.of(chatPrompt));
    when(registry.getApprovalGate("admin_approval")).thenReturn(Optional.of(gateDef));

    var player = createPlayer();
    var interceptResult =
        engine.intercept(player, "/approve <@confirm_preset> <!gate:@admin_approval>");
    assertTrue(interceptResult.isPresent());
    assertTrue(interceptResult.get().hasPrompts());

    // Verify session was created and contains the adjusted tag with preserved properties and gate
    var sessionOpt = engine.getSession(player);
    assertTrue(sessionOpt.isPresent());
    var session = sessionOpt.get();
    assertEquals(1, session.parsedCommand().promptTags().size());
    var sessionTag = session.parsedCommand().promptTags().get(0);
    assertFalse(
        sessionTag.sanitize(), "Preset definition sanitize=false must take effect in session tag");
    assertEquals(
        1,
        session.parsedCommand().preDispatchGates().size(),
        "PreDispatchGates must be preserved in session");
    assertEquals(
        "admin_approval",
        ((PreDispatchGateSpec.Approval) session.parsedCommand().preDispatchGates().get(0))
            .presetId());
  }

  @Test
  @DisplayName(
      "applyPresetSanitize returns original ParsedCommand unchanged when no preset tags exist")
  void applyPresetSanitizeNoPresetsReturnsSameInstance() {
    var gateSpec = new PreDispatchGateSpec.Approval("audit_gate");
    var tag = new PromptTag("<name>", "", "", "name");
    var parsed =
        new ParsedCommand(
            "/cmd \"\"",
            List.of(tag),
            List.of(),
            List.of(gateSpec),
            new ParserConfig("<", ">", "\\"),
            "/cmd <name>",
            List.of());

    var result = engine.applyPresetSanitize(parsed, null);
    assertSame(parsed, result, "Should return identical instance if no preset tags exist");
  }
}
