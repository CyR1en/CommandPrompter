package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.custom.CustomScreenAuditLogger;
import dev.cyr1en.promptpaper.custom.CustomScreenFactory;
import dev.cyr1en.promptpaper.custom.CustomScreenRegistry;
import dev.cyr1en.promptpaper.custom.ScreenKeyResolver;
import dev.cyr1en.promptpaper.preset.CancelBehavior;
import dev.cyr1en.promptpaper.preset.ChatPrompt;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.ExecutionPolicy;
import dev.cyr1en.promptpaper.preset.PostCommand;
import dev.cyr1en.promptpaper.preset.PresetRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromptEngineInterceptResultTest extends MockBukkitTest {

  private CustomScreenRegistry customRegistry;
  private ScreenKeyResolver resolver;
  private PromptEngine engine;
  private PresetRegistry presetRegistry;
  private Plugin testPlugin;
  private CustomScreenFactory dummyFactory;

  @BeforeEach
  void setUp() {
    customRegistry =
        new CustomScreenRegistry(
            () -> true,
            Map::of,
            Set.of(
                "a",
                "anvil",
                "s",
                "sign",
                "p",
                "player",
                "d",
                "dialog",
                "c",
                "confirm",
                "confirmation"),
            CustomScreenAuditLogger.noop());
    resolver =
        new ScreenKeyResolver(
            customRegistry, Map.of("mapped_anvil", dev.cyr1en.promptpaper.config.ScreenType.ANVIL));
    presetRegistry = mock(PresetRegistry.class);
    when(plugin.getPresetRegistry()).thenReturn(presetRegistry);

    pluginLogger = spy(pluginLogger);
    when(plugin.getPluginLogger()).thenReturn(pluginLogger);

    engine = new PromptEngine(plugin, scheduler, resolver);

    testPlugin = mock(Plugin.class);
    when(testPlugin.getName()).thenReturn("TestPlugin");
    when(testPlugin.isEnabled()).thenReturn(true);
    dummyFactory = (player, tag) -> null;
  }

  @Test
  @DisplayName("Registered custom screen key returns STARTED and starts one session")
  void testRegisteredCustomKeyStartsSession() {
    customRegistry.registerScreen(testPlugin, "my_custom", dummyFactory);
    var player = createPlayer("CustomUser");

    var result = engine.interceptResult(player, "/cmd <my_custom:Enter value>");

    assertInstanceOf(InterceptResult.Started.class, result);
    assertTrue(result.isStarted());
    assertTrue(engine.hasActiveSession(player));
    assertEquals(
        "my_custom", ((InterceptResult.Started) result).parsedCommand().promptTags().get(0).key());
  }

  @Test
  @DisplayName("Unknown screen key returns REJECTED_FAIL_CLOSED and creates no session")
  void testUnknownScreenKeyRejectsFailClosed() {
    var player = createPlayer("UnknownUser");

    var result = engine.interceptResult(player, "/cmd <unknown_key:Enter value>");

    assertInstanceOf(InterceptResult.RejectedFailClosed.class, result);
    assertTrue(result.isRejectedFailClosed());
    assertFalse(engine.hasActiveSession(player));
    verify(plugin.getConfigLoader().getI18n(), atLeastOnce())
        .get(eq("command.error.missing_preset"), same(player));
  }

  @Test
  @DisplayName("Uppercase and mixed-case custom keys follow canonical grammar")
  void testCaseInsensitiveCustomKeyResolution() {
    customRegistry.registerScreen(testPlugin, "my_gui", dummyFactory);
    var player = createPlayer("CaseUser");

    var result = engine.interceptResult(player, "/cmd <MY_GUI:Enter value>");

    assertInstanceOf(InterceptResult.Started.class, result);
    assertTrue(engine.hasActiveSession(player));
  }

  @Test
  @DisplayName("Invalid key in second prompt rejects entire command with no partial session")
  void testMultiPromptInvalidSecondKeyRejectsEntireCommand() {
    customRegistry.registerScreen(testPlugin, "valid_key", dummyFactory);
    var player = createPlayer("MultiUser");

    var result = engine.interceptResult(player, "/cmd <valid_key:First> <bad_key:Second>");

    assertInstanceOf(InterceptResult.RejectedFailClosed.class, result);
    assertFalse(engine.hasActiveSession(player));
  }

  @Test
  @DisplayName("Unregistering provider before intercept rejects subsequent attempts")
  void testUnregisteredProviderRejectsFailClosed() {
    customRegistry.registerScreen(testPlugin, "ephemeral", dummyFactory);
    var player = createPlayer("EphemeralUser");

    // Before unregister: starts session
    var res1 = engine.interceptResult(player, "/cmd <ephemeral:Enter>");
    assertInstanceOf(InterceptResult.Started.class, res1);
    engine.cancel(player, dev.cyr1en.promptcore.CancelReason.MANUAL);

    // Unregister provider
    customRegistry.unregisterScreens(testPlugin);

    // After unregister: rejected fail-closed
    var res2 = engine.interceptResult(player, "/cmd <ephemeral:Enter>");
    assertInstanceOf(InterceptResult.RejectedFailClosed.class, res2);
    assertFalse(engine.hasActiveSession(player));
  }

  @Test
  @DisplayName("Built-ins and configured mappings return STARTED")
  void testBuiltInsAndConfiguredMappings() {
    var player = createPlayer("BuiltInUser");

    assertInstanceOf(
        InterceptResult.Started.class, engine.interceptResult(player, "/cmd <a:Anvil>"));
    engine.discard(player.getUniqueId());

    assertInstanceOf(
        InterceptResult.Started.class, engine.interceptResult(player, "/cmd <s:Sign>"));
    engine.discard(player.getUniqueId());

    assertInstanceOf(
        InterceptResult.Started.class, engine.interceptResult(player, "/cmd <p:Player>"));
    engine.discard(player.getUniqueId());

    assertInstanceOf(
        InterceptResult.Started.class, engine.interceptResult(player, "/cmd <d:Dialog>"));
    engine.discard(player.getUniqueId());

    assertInstanceOf(
        InterceptResult.Started.class, engine.interceptResult(player, "/cmd <c:Confirm>"));
    engine.discard(player.getUniqueId());

    assertInstanceOf(InterceptResult.Started.class, engine.interceptResult(player, "/cmd <:Chat>"));
    engine.discard(player.getUniqueId());

    assertInstanceOf(
        InterceptResult.Started.class,
        engine.interceptResult(player, "/cmd <mapped_anvil:Mapped>"));
    engine.discard(player.getUniqueId());
  }

  @Test
  @DisplayName("Known preset prompt returns STARTED")
  void testKnownPresetPrompt() {
    var chat =
        new ChatPrompt("chat", "my_preset", "Why?", new CancelBehavior(false, "", false, ""), true);
    when(presetRegistry.getPrompt("my_preset")).thenReturn(Optional.of(chat));
    var player = createPlayer("PresetUser");

    var result = engine.interceptResult(player, "/cmd <@my_preset>");

    assertInstanceOf(InterceptResult.Started.class, result);
    assertTrue(engine.hasActiveSession(player));
  }

  @Test
  @DisplayName("Known preset post-command with no prompts returns NO_PROMPTS")
  void testKnownPresetPostCommandNoPrompts() {
    var pc =
        new PostCommand(
            "log_preset", "say {player} hi", ExecutionPolicy.ON_COMPLETE, ExecuteAs.CONSOLE, 0);
    when(presetRegistry.getPostCommand("log_preset")).thenReturn(Optional.of(pc));
    var player = createPlayer("PcmUser");

    var result = engine.interceptResult(player, "/cmd <!@log_preset>");

    assertInstanceOf(InterceptResult.NoPrompts.class, result);
    assertFalse(engine.hasActiveSession(player));
  }

  @Test
  @DisplayName("Missing preset prompt returns REJECTED_FAIL_CLOSED")
  void testMissingPresetPromptRejects() {
    when(presetRegistry.getPrompt("missing_id")).thenReturn(Optional.empty());
    var player = createPlayer("MissingPresetUser");

    var result = engine.interceptResult(player, "/cmd <@missing_id>");

    assertInstanceOf(InterceptResult.RejectedFailClosed.class, result);
    assertFalse(engine.hasActiveSession(player));
  }

  @Test
  @DisplayName("Missing validator alias returns REJECTED_FAIL_CLOSED")
  void testMissingValidatorAliasRejects() {
    when(promptConfig.hasValidator("unknown_val")).thenReturn(false);
    var player = createPlayer("ValUser");

    var result = engine.interceptResult(player, "/cmd <a:why -iv:unknown_val>");

    assertInstanceOf(InterceptResult.RejectedFailClosed.class, result);
    assertFalse(engine.hasActiveSession(player));
  }

  @Test
  @DisplayName("Structural parse errors return REJECTED_FAIL_CLOSED")
  void testStructuralParseErrorsReject() {
    var player = createPlayer("SyntaxUser");

    var result = engine.interceptResult(player, "/cmd <a:why -timeout -10>");

    assertInstanceOf(InterceptResult.RejectedFailClosed.class, result);
    assertFalse(engine.hasActiveSession(player));
  }

  @Test
  @DisplayName("Plain command with no prompt tags returns NO_PROMPTS")
  void testPlainCommandReturnsNoPrompts() {
    var player = createPlayer("PlainUser");

    var result = engine.interceptResult(player, "/cmd normal arguments");

    assertInstanceOf(InterceptResult.NoPrompts.class, result);
    assertFalse(engine.hasActiveSession(player));
  }

  @Test
  @DisplayName("Permission check failure returns REJECTED_PERMISSION")
  void testPermissionRejection() {
    when(config.enablePermission()).thenReturn(true);
    var player = mock(org.bukkit.entity.Player.class);
    when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
    when(player.getName()).thenReturn("NoPermUser");
    when(player.hasPermission("promptpaper.use")).thenReturn(false);

    var result = engine.interceptResult(player, "/cmd <a:prompt>");

    assertInstanceOf(InterceptResult.RejectedPermission.class, result);
    assertFalse(result.isStarted());
    assertFalse(result.isRejectedFailClosed());
  }

  @Test
  @DisplayName("Active session rejection returns REJECTED_ACTIVE_SESSION")
  void testActiveSessionRejection() {
    var player = createPlayer("ActiveUser");

    var res1 = engine.interceptResult(player, "/cmd <a:first>");
    assertInstanceOf(InterceptResult.Started.class, res1);

    var res2 = engine.interceptResult(player, "/cmd <a:second>");
    assertInstanceOf(InterceptResult.RejectedActiveSession.class, res2);
    assertTrue(res2.isRejectedActiveSession());
  }

  @Test
  @DisplayName("Legacy intercept() wrapper returns Optional.of only for STARTED")
  void testLegacyInterceptWrapper() {
    var player = createPlayer("LegacyUser");

    // Started -> present
    var res1 = engine.intercept(player, "/cmd <a:test>");
    assertTrue(res1.isPresent());

    // Active session -> empty
    var res2 = engine.intercept(player, "/cmd <a:test2>");
    assertTrue(res2.isEmpty());
    engine.discard(player.getUniqueId());

    // Unknown screen -> empty
    var res3 = engine.intercept(player, "/cmd <bad_screen:test>");
    assertTrue(res3.isEmpty());

    // Plain command -> empty
    var res4 = engine.intercept(player, "/cmd plain");
    assertTrue(res4.isEmpty());
  }

  @Test
  @DisplayName("Diagnostics safely sanitize C0 controls and truncate long unknown keys")
  void testDiagnosticsSanitizationAndTruncation() {
    var player = createPlayer("DiagUser");
    String dangerousKey = "bad\u0000key\u001Fname" + "a".repeat(100);

    var result = engine.interceptResult(player, "/cmd <" + dangerousKey + ":prompt>");

    assertInstanceOf(InterceptResult.RejectedFailClosed.class, result);
    verify(plugin.getPluginLogger())
        .warn(
            argThat(
                msg ->
                    msg.contains("badkeyname")
                        && !msg.contains("\u0000")
                        && !msg.contains("\u001F")
                        && msg.contains("DiagUser")));
  }
}
