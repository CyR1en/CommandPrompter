package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
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
import java.util.List;
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

  @Test
  void presetSanitizeFlagFromDefinitionReachesSessionTag() {
    // #77: the parser defaults preset tags to sanitize=true, but the preset definition is
    // authoritative. sanitize=false keeps §cHello intact on the screen path; sanitize=true
    // strips it. Each player can only hold one session, so both sides need their own player.
    when(registry.getPrompt("no_san")).thenReturn(Optional.of(
            new ChatPrompt("chat", "no_san", "Why?", new CancelBehavior(false, "", false, ""), false)));
    when(registry.getPrompt("san")).thenReturn(Optional.of(
            new ChatPrompt("chat", "san", "Why?", new CancelBehavior(false, "", false, ""), true)));

    var noSanPlayer = createPlayer("NoSan");
    var noSanResult = engine.intercept(noSanPlayer, "/cmd <@no_san>");
    assertTrue(noSanResult.isPresent());
    var noSanTag = engine.getSession(noSanPlayer).orElseThrow().currentPrompt().orElseThrow();
    assertFalse(noSanTag.sanitize(), "sanitize=false preset must preserve §cHello");
    assertEquals("no_san", noSanTag.displayText());
    assertEquals(List.of("§cHello"), engine.submit(noSanPlayer, "§cHello").orElseThrow().answers());

    var sanPlayer = createPlayer("San");
    var sanResult = engine.intercept(sanPlayer, "/cmd <@san>");
    assertTrue(sanResult.isPresent());
    var sanTag = engine.getSession(sanPlayer).orElseThrow().currentPrompt().orElseThrow();
    assertTrue(sanTag.sanitize(), "sanitize=true preset must strip §cHello");
    assertEquals("san", sanTag.displayText());
    assertEquals(List.of("Hello"), engine.submit(sanPlayer, "§cHello").orElseThrow().answers());
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
    var player = createPlayer();
    var i18n = plugin.getConfigLoader().getI18n();
    when(i18n.get(eq("command.error.missing_preset"), same(player)))
        .thenReturn(Component.text("missing preset error"));

    engine.intercept(player, "/cmd <@nope>");

    // The engine must send a localized message with the player as the i18n
    // context; the exact key is command.error.missing_preset, but the value is
    // what reaches the player.
    verify(i18n, times(1)).get(eq("command.error.missing_preset"), same(player));
  }

  @Test
  void noRegistryTreatedAsAllPresetsMissing() {
    // Belt-and-suspenders: if the registry is null (e.g. early startup),
    // the engine treats every preset ref as missing so the player never
    // sees a silent pass for a half-loaded config.
    when(plugin.getPresetRegistry()).thenReturn(null);
    engine = new PromptEngine(plugin, scheduler);
    var player = createPlayer();
    var i18n = plugin.getConfigLoader().getI18n();
    when(i18n.get(eq("command.error.missing_preset"), same(player)))
        .thenReturn(Component.text("missing preset error"));

    var result = engine.intercept(player, "/cmd <@any_id>");

    assertTrue(result.isEmpty());
    verify(i18n, times(1)).get(eq("command.error.missing_preset"), same(player));
  }

  @Test
  void commandWithKnownValidatorStartsSession() {
    when(promptConfig.hasValidator("req")).thenReturn(true);
    var result = engine.intercept(createPlayer(), "/cmd <a:why -iv:req>");
    assertTrue(result.isPresent());
  }

  @Test
  void commandWithUnknownValidatorFailsFast() {
    var player = createPlayer();
    var i18n = plugin.getConfigLoader().getI18n();
    when(promptConfig.hasValidator("unknown_val")).thenReturn(false);

    var result = engine.intercept(player, "/cmd <a:why -iv:unknown_val>");

    assertTrue(result.isEmpty());
    verify(i18n, times(1)).get(eq("command.error.missing_validator"), same(player));
  }

  @Test
  void compoundCommandWithUnknownValidatorFailsFast() {
    var player = createPlayer();
    var i18n = plugin.getConfigLoader().getI18n();
    when(promptConfig.hasValidator("bad_val")).thenReturn(false);

    var result = engine.intercept(player, "/cmd <d:choice[a,b]:One && d:text:Two -iv:bad_val>");

    assertTrue(result.isEmpty());
    verify(i18n, times(1)).get(eq("command.error.missing_validator"), same(player));
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

  // --- SEC-02: structural parse failure diagnostics ---

  @Test
  void structuralParserErrorRejectsInvalidTimeoutAndAbortsSession() {
    var player = createPlayer("Sec02TimeoutUser");

    var res1 = engine.intercept(player, "/cmd <a:test -timeout abc>");
    assertTrue(res1.isEmpty());
    assertFalse(engine.hasActiveSession(player));
    assertTrue(engine.hasStructuralParseError("/cmd <a:test -timeout abc>"));

    var res2 = engine.intercept(player, "/cmd <a:test -timeout -5>");
    assertTrue(res2.isEmpty());
    assertFalse(engine.hasActiveSession(player));
    assertTrue(engine.hasStructuralParseError("/cmd <a:test -timeout -5>"));

    var res3 = engine.intercept(player, "/cmd <a:test -timeout 9999>");
    assertTrue(res3.isEmpty());
    assertFalse(engine.hasActiveSession(player));
    assertTrue(engine.hasStructuralParseError("/cmd <a:test -timeout 9999>"));
  }

  @Test
  void structuralParserErrorExceedingTagLimitAbortsSession() {
    var player = createPlayer("Sec02LimitUser");
    var sb = new StringBuilder("/cmd");
    for (int i = 0; i < 17; i++) {
      sb.append(" <a:t").append(i).append(">");
    }
    var cmd = sb.toString();

    var result = engine.intercept(player, cmd);
    assertTrue(result.isEmpty());
    assertFalse(engine.hasActiveSession(player));
    assertTrue(engine.hasStructuralParseError(cmd));
  }

  // --- Gate 4 Remediation: Finding 1, Finding 3 (Prompt Side), Finding 4 ---

  @Test
  void commandWithApprovalGateAndZeroPromptsRejectsFailClosedNeverPassesThrough() {
    var player = createPlayer("GateZeroUser");
    var gate = new dev.cyr1en.promptpaper.preset.ApprovalGateDefinition(
            "admin_gate",
            dev.cyr1en.promptcore.logic.transform.TemplateCompiler.compile("admin"),
            dev.cyr1en.promptcore.logic.transform.TemplateCompiler.compile("Approve?"),
            30,
            dev.cyr1en.promptpaper.preset.SelfApprovalPolicy.AUTO_APPROVE,
            null);
    var snapshot = new dev.cyr1en.promptpaper.preset.PresetSnapshot(
            java.util.Map.of(),
            java.util.Map.of(),
            java.util.Map.of("admin_gate", gate),
            java.util.Map.of(),
            1L);
    when(registry.getSnapshot()).thenReturn(snapshot);

    // Command with gate but 0 prompts
    var result = engine.interceptResult(player, "/pay Bob 100 <!gate:@admin_gate>");
    assertInstanceOf(InterceptResult.RejectedFailClosed.class, result);
    assertFalse(result.isStarted());
    assertFalse(engine.hasActiveSession(player));

    // Verify bounded fail-closed classification
    assertTrue(engine.hasPresetReferences("/pay Bob 100 <!gate:@admin_gate>"));
  }

  @Test
  void gateValidationConsultsCapturedSnapshotOnly_capturedMissingLivePresentRejects() {
    var player = createPlayer("SnapTestUser1");

    // Live registry has gate1, but captured snapshot at inception lacks gate1
    var gate = new dev.cyr1en.promptpaper.preset.ApprovalGateDefinition(
            "gate1",
            dev.cyr1en.promptcore.logic.transform.TemplateCompiler.compile("admin"),
            dev.cyr1en.promptcore.logic.transform.TemplateCompiler.compile("Approve?"),
            30,
            dev.cyr1en.promptpaper.preset.SelfApprovalPolicy.AUTO_APPROVE,
            null);
    when(registry.getApprovalGate("gate1")).thenReturn(Optional.of(gate));
    when(registry.getSnapshot()).thenReturn(dev.cyr1en.promptpaper.preset.PresetSnapshot.empty());

    var result = engine.interceptResult(player, "/pay <a:amt> <!gate:@gate1>");
    assertInstanceOf(InterceptResult.RejectedFailClosed.class, result);
    assertFalse(engine.hasActiveSession(player));
  }

  @Test
  void gateValidationConsultsCapturedSnapshotOnly_capturedPresentLiveMissingAccepts() {
    var player = createPlayer("SnapTestUser2");

    // Captured snapshot has gate1, but live registry lacks gate1
    var gate = new dev.cyr1en.promptpaper.preset.ApprovalGateDefinition(
            "gate1",
            dev.cyr1en.promptcore.logic.transform.TemplateCompiler.compile("admin"),
            dev.cyr1en.promptcore.logic.transform.TemplateCompiler.compile("Approve?"),
            30,
            dev.cyr1en.promptpaper.preset.SelfApprovalPolicy.AUTO_APPROVE,
            null);
    var snapshot = new dev.cyr1en.promptpaper.preset.PresetSnapshot(
            java.util.Map.of(),
            java.util.Map.of(),
            java.util.Map.of("gate1", gate),
            java.util.Map.of(),
            1L);
    when(registry.getSnapshot()).thenReturn(snapshot);
    when(registry.getApprovalGate("gate1")).thenReturn(Optional.empty());

    var result = engine.interceptResult(player, "/pay <a:amt> <!gate:@gate1>");
    assertInstanceOf(InterceptResult.Started.class, result);
    assertTrue(engine.hasActiveSession(player));
  }

  @Test
  void activeApprovalLeaseRejectsSessionInception() {
    var player = createPlayer("LeasedUser");
    var leaseRegistry = new dev.cyr1en.promptpaper.approval.PlayerInteractionLeaseRegistry();
    var execId = dev.cyr1en.promptpaper.execution.runtime.ExecutionId.create();
    leaseRegistry.acquire(player.getUniqueId(), execId, java.time.Duration.ofMinutes(1));

    engine.setLeaseRegistry(leaseRegistry);

    var result = engine.interceptResult(player, "/cmd <a:prompt>");
    assertInstanceOf(InterceptResult.RejectedActiveSession.class, result);
    assertTrue(result.isRejectedActiveSession());
    assertFalse(engine.hasActiveSession(player));
  }

  // ====================================================================
  // Security & Remediation Tests (Hacker M-2 & Inline Delay Residuals)
  // ====================================================================

  @Test
  void failFastMissingLogsSanitizedUuidCategoryAndMissingIdsOnly() {
    var player = createPlayer("AttackerUser");
    var loggerSpy = org.mockito.Mockito.spy(pluginLogger);
    when(plugin.getPluginLogger()).thenReturn(loggerSpy);
    when(registry.getPrompt(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());

    String rawCommand =
        "/secret_cmd <@x\"<br><red>\n\u0000secret\"y> <@missing_preset>";
    var result = engine.interceptResult(player, rawCommand);

    assertInstanceOf(InterceptResult.RejectedFailClosed.class, result);
    assertFalse(engine.hasActiveSession(player));

    var captor = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(loggerSpy).err(captor.capture());
    String loggedMsg = captor.getValue();

    assertTrue(loggedMsg.contains(player.getUniqueId().toString()), "Must contain player UUID");
    assertTrue(loggedMsg.contains("prompts="), "Must contain prompts category");
    assertTrue(loggedMsg.contains("missing_preset"), "Must contain missing prompt ID");
    assertTrue(
        loggedMsg.contains("\\<br>\\<red>"),
        "Must escape MiniMessage tags in preset ID");
    assertFalse(
        loggedMsg.matches(".*(?<!\\\\)<(br|red|newline|green)>.*"),
        "Must not contain unescaped MiniMessage tags");
    assertFalse(loggedMsg.contains("\n"), "Must not contain line break");
    assertFalse(loggedMsg.contains("\r"), "Must not contain carriage return");
    assertFalse(loggedMsg.contains("\u0000"), "Must not contain NUL control char");
    assertFalse(loggedMsg.contains("/secret_cmd"), "Must not contain raw command");
  }

  @Test
  void inlinePcmDelay_boundsAndOverflowFailClosed_noSessionCreated() {
    var player = createPlayer("PcmPlayer");

    // Overflow / excessive delay
    var resOverflow = engine.interceptResult(player, "/cmd <a:prompt> <!:72001 say hi>");
    assertInstanceOf(InterceptResult.RejectedFailClosed.class, resOverflow);
    assertFalse(engine.hasActiveSession(player));

    var resHuge =
        engine.interceptResult(player, "/cmd <a:prompt> <!:999999999999999999999999 say hi>");
    assertInstanceOf(InterceptResult.RejectedFailClosed.class, resHuge);
    assertFalse(engine.hasActiveSession(player));

    // Non-numeric / malformed delay
    var resNonNumeric = engine.interceptResult(player, "/cmd <a:prompt> <!:abc say hi>");
    assertInstanceOf(InterceptResult.RejectedFailClosed.class, resNonNumeric);
    assertFalse(engine.hasActiveSession(player));

    var resEmpty = engine.interceptResult(player, "/cmd <a:prompt> <!: say hi>");
    assertInstanceOf(InterceptResult.RejectedFailClosed.class, resEmpty);
    assertFalse(engine.hasActiveSession(player));

    var resNegative = engine.interceptResult(player, "/cmd <a:prompt> <!:-1 say hi>");
    assertInstanceOf(InterceptResult.RejectedFailClosed.class, resNegative);
    assertFalse(engine.hasActiveSession(player));

    // Valid boundaries (0 and 72000) succeed
    var resZero = engine.interceptResult(player, "/cmd <a:prompt> <!:0 say hi>");
    assertInstanceOf(InterceptResult.Started.class, resZero);
    assertTrue(engine.hasActiveSession(player));
    engine.cancel(player, dev.cyr1en.promptcore.CancelReason.MANUAL);

    var resMax = engine.interceptResult(player, "/cmd <a:prompt> <!:72000 say hi>");
    assertInstanceOf(InterceptResult.Started.class, resMax);
    assertTrue(engine.hasActiveSession(player));
  }

  @Test
  void failFastMissing_capsSummaryLengthAndIndividualIds() {
    var player = createPlayer("LongIdUser");
    var loggerSpy = org.mockito.Mockito.spy(pluginLogger);
    when(plugin.getPluginLogger()).thenReturn(loggerSpy);
    when(registry.getPrompt(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());

    String longId = "a".repeat(100);
    var sb = new StringBuilder("/cmd");
    for (int i = 0; i < 10; i++) {
      sb.append(" <@").append(longId).append(i).append(">");
    }

    var result = engine.interceptResult(player, sb.toString());
    assertInstanceOf(InterceptResult.RejectedFailClosed.class, result);

    var captor = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(loggerSpy).err(captor.capture());
    String loggedMsg = captor.getValue();

    // Verify summary is bounded
    int summaryStart = loggedMsg.indexOf('[');
    int summaryEnd = loggedMsg.indexOf(']');
    assertTrue(summaryStart >= 0, "Must contain summary opener '['");
    if (summaryEnd >= 0) {
      String summary = loggedMsg.substring(summaryStart + 1, summaryEnd);
      assertTrue(summary.length() <= 256, "Summary length must be <= 256: " + summary.length());
    }
  }
}
