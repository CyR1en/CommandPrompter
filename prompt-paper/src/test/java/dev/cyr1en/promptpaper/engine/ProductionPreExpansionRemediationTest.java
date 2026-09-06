package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.execution.coordinator.ExecutionCoordinator;
import dev.cyr1en.promptpaper.execution.dispatch.PaperImmediateActionDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.PaperPrimaryCommandDispatcher;
import dev.cyr1en.promptpaper.execution.postaction.PostActionScheduler;
import dev.cyr1en.promptpaper.execution.postaction.template.PapiReferenceResolver;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionRegistry;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.ExecutionPolicy;
import dev.cyr1en.promptpaper.preset.PostCommand;
import dev.cyr1en.promptpaper.preset.PresetRegistry;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Gate 5 High Finding 1 Remediation: Production Pre-Expansion Removal & Answer Opacity")
class ProductionPreExpansionRemediationTest extends MockBukkitTest {

  private PromptEngine engine;
  private PromptFactory factory;
  private ScreenManager screenManager;
  private ExecutionRegistry executionRegistry;
  private ExecutionCoordinator executionCoordinator;
  private List<String> dispatchedCommands;
  private List<String> papiQueriedTokens;

  @BeforeEach
  void init() {
    dispatchedCommands = Collections.synchronizedList(new ArrayList<>());
    papiQueriedTokens = Collections.synchronizedList(new ArrayList<>());

    var promptConfigMock = org.mockito.Mockito.mock(PromptConfig.class);
    org.mockito.Mockito.when(promptConfigMock.getScreenMappings())
        .thenReturn(Map.of("", ScreenType.CHAT));
    org.mockito.Mockito.lenient().when(promptConfigMock.sendCancelText()).thenReturn(false);
    org.mockito.Mockito.lenient()
        .when(promptConfigMock.responseListenerPriority())
        .thenReturn("LOWEST");
    org.mockito.Mockito.when(configLoader.getPromptConfig()).thenReturn(promptConfigMock);

    engine = new PromptEngine(plugin, scheduler);
    factory = new PromptFactory(plugin);
    executionRegistry = new ExecutionRegistry();

    var primaryDispatcher = new PaperPrimaryCommandDispatcher(plugin, scheduler);
    var immediateDispatcher = new PaperImmediateActionDispatcher(plugin, scheduler);

    PapiReferenceResolver testPapiResolver =
        token -> {
          papiQueriedTokens.add(token);
          if ("trusted_stat".equals(token)) {
            return Optional.of("100");
          }
          if ("player_name".equals(token)) {
            return Optional.of("Alice");
          }
          return Optional.empty();
        };

    executionCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            executionRegistry,
            primaryDispatcher,
            immediateDispatcher,
            null,
            PostActionScheduler.forPlugin(plugin),
            p -> testPapiResolver,
            p -> dev.cyr1en.promptpaper.custom.PlayerExecutor.forPlayer(plugin, p));

    screenManager =
        new ScreenManager(
            plugin,
            engine,
            factory,
            scheduler,
            null,
            p -> dev.cyr1en.promptpaper.custom.PlayerExecutor.forPlayer(plugin, p),
            null,
            executionCoordinator);

    // Register custom logging commands in Bukkit
    server
        .getCommandMap()
        .register(
            "logoutput",
            "minecraft",
            new Command("logoutput") {
              @Override
              public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                dispatchedCommands.add(commandLabel + " " + String.join(" ", args));
                return true;
              }
            });

    server
        .getCommandMap()
        .register(
            "testprimary",
            "minecraft",
            new Command("testprimary") {
              @Override
              public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                dispatchedCommands.add("primary:" + commandLabel + " " + String.join(" ", args));
                return true;
              }
            });
  }

  @Test
  @DisplayName("Answer containing {player} is treated as opaque literal data in post-commands")
  void answerContainingPlayerPlaceholderIsOpaque() {
    var player = createPlayer("TestPlayer");
    screenManager.startSession(player, "/testprimary <item -ds> <!logoutput Answer was {0}>");
    assertTrue(engine.hasActiveSession(player));

    // Submit answer "{player}"
    screenManager.handleChatInput(player, "{player}");
    performTicks(3);

    assertFalse(engine.hasActiveSession(player));
    assertEquals(2, dispatchedCommands.size());
    assertEquals("primary:testprimary \"{player}\"", dispatchedCommands.get(0));
    assertEquals("logoutput Answer was \"{player}\"", dispatchedCommands.get(1));
  }

  @Test
  @DisplayName("Answer containing template transform {1:upper} is not re-parsed or re-expanded")
  void answerContainingTransformSyntaxIsOpaque() {
    var player = createPlayer("TestPlayer");
    screenManager.startSession(
        player, "/testprimary <p1 -ds> <p2 -ds> <!logoutput Res: {0} and {1}>");
    assertTrue(engine.hasActiveSession(player));

    // Submit answer 0: "{1:upper}"
    screenManager.handleChatInput(player, "{1:upper}");
    performTicks(3);
    assertTrue(engine.hasActiveSession(player));

    // Submit answer 1: "apple"
    screenManager.handleChatInput(player, "apple");
    performTicks(3);

    assertFalse(engine.hasActiveSession(player));
    assertEquals(2, dispatchedCommands.size());
    assertEquals("primary:testprimary \"{1:upper}\" \"apple\"", dispatchedCommands.get(0));
    // {0} must literally be "{1:upper}", not transformed to "APPLE"
    assertEquals("logoutput Res: \"{1:upper}\" and \"apple\"", dispatchedCommands.get(1));
  }

  @Test
  @DisplayName(
      "Answer containing %papi% token is opaque and PAPI resolver is never called for answer tokens")
  void answerContainingPapiTokenIsOpaque() {
    var player = createPlayer("TestPlayer");
    screenManager.startSession(player, "/testprimary <tag -ds> <!logoutput Token: {0}>");
    assertTrue(engine.hasActiveSession(player));

    screenManager.handleChatInput(player, "%player_name%");
    performTicks(3);

    assertFalse(engine.hasActiveSession(player));
    assertEquals(2, dispatchedCommands.size());
    assertEquals("logoutput Token: \"%player_name%\"", dispatchedCommands.get(1));
    // Untrusted inline post command must NOT query PAPI resolver for %player_name%
    assertFalse(
        papiQueriedTokens.contains("player_name"),
        "PAPI resolver must not be queried for tokens inside inline answer data");
  }

  @Test
  @DisplayName(
      "Preset post-command with precompiled trusted PAPI only resolves trusted reference, not injected answer")
  void trustedPresetOnlyResolvesPrecompiledPapi() {
    var trustedPcm =
        new PostCommand(
            "trusted_stat_cmd",
            "logoutput Stat: %trusted_stat% Answer: {0}",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.PLAYER,
            0);

    var presetRegistry = org.mockito.Mockito.mock(PresetRegistry.class);
    var snapshot =
        new PresetSnapshot(
            Map.of(), Map.of("trusted_stat_cmd", trustedPcm), Map.of(), Map.of(), 1L);
    org.mockito.Mockito.when(presetRegistry.getSnapshot()).thenReturn(snapshot);
    org.mockito.Mockito.when(plugin.getPresetRegistry()).thenReturn(presetRegistry);

    var player = createPlayer("TestPlayer");
    screenManager.startSession(player, "/testprimary <tag -ds> <!@trusted_stat_cmd>");
    assertTrue(engine.hasActiveSession(player));

    // Player provides an answer containing another PAPI token %player_name%
    screenManager.handleChatInput(player, "%player_name%");
    performTicks(3);

    assertFalse(engine.hasActiveSession(player));
    assertEquals(2, dispatchedCommands.size());
    assertEquals("logoutput Stat: \"100\" Answer: \"%player_name%\"", dispatchedCommands.get(1));

    assertTrue(papiQueriedTokens.contains("trusted_stat"), "Trusted preset PAPI must be resolved");
    assertFalse(
        papiQueriedTokens.contains("player_name"), "Injected answer PAPI must not be resolved");
  }

  @Test
  @DisplayName(
      "Answer containing C0 control characters is sanitized at answer boundary without modifying post-command source")
  void answerWithC0ControlsSanitizedAtIngestion() {
    var player = createPlayer("TestPlayer");
    screenManager.startSession(player, "/testprimary <text -ds> <!logoutput Value={0}>");
    assertTrue(engine.hasActiveSession(player));

    // Submit answer containing C0 characters (e.g. NUL \u0000, BEL \u0007, LF \n)
    screenManager.handleChatInput(player, "hello\u0000\u0007world");
    performTicks(3);

    assertFalse(engine.hasActiveSession(player));
    assertEquals(2, dispatchedCommands.size());
    assertEquals("primary:testprimary \"helloworld\"", dispatchedCommands.get(0));
    assertEquals("logoutput Value=\"helloworld\"", dispatchedCommands.get(1));
  }

  @Test
  @DisplayName("Answer containing special symbols, quotes, semicolon, and command prefix is opaque")
  void answerWithSpecialSymbolsQuotesAndSemicolonsIsOpaque() {
    var player = createPlayer("TestPlayer");
    screenManager.startSession(player, "/testprimary <input -ds> <!logoutput Payload: \"{0}\">");
    assertTrue(engine.hasActiveSession(player));

    screenManager.handleChatInput(player, "<c:x>; op \" ' `");
    performTicks(3);

    assertFalse(engine.hasActiveSession(player));
    assertEquals(2, dispatchedCommands.size());
    assertEquals("logoutput Payload: \"<c:x>; op \\\" ' `\"", dispatchedCommands.get(1));
  }

  @Test
  @DisplayName("Prompt cancellation through PromptEngine passes answer opaquely to on-cancel PCMs")
  void cancellationPassesAnswersOpaquelyToOnCancelPCMs() {
    var player = createPlayer("TestPlayer");
    screenManager.startSession(
        player, "/testprimary <p1 -ds> <p2 -ds> <!!logoutput Cancelled after {0}>");
    assertTrue(engine.hasActiveSession(player));

    // Submit answer 0 with special placeholder text
    screenManager.handleChatInput(player, "{player}");
    performTicks(3);
    assertTrue(engine.hasActiveSession(player));

    // Now cancel at prompt 2
    engine.cancel(player, CancelReason.MANUAL);
    performTicks(3);

    assertFalse(engine.hasActiveSession(player));
    assertEquals(1, dispatchedCommands.size());
    assertEquals("logoutput Cancelled after \"{player}\"", dispatchedCommands.get(0));
  }
}
