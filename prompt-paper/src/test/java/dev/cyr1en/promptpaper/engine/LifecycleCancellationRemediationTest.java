package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.command.ReloadCommand;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.custom.ActiveScreenHandle;
import dev.cyr1en.promptpaper.custom.CustomScreenHandle;
import dev.cyr1en.promptpaper.execution.coordinator.ExecutionCoordinator;
import dev.cyr1en.promptpaper.execution.dispatch.PaperImmediateActionDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.PaperPrimaryCommandDispatcher;
import dev.cyr1en.promptpaper.execution.postaction.PostActionScheduler;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionRegistry;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Gate 5 High Finding 2 Remediation: Explicit Cancellation Mode & Teardown Seams")
class LifecycleCancellationRemediationTest extends MockBukkitTest {

  private PromptEngine engine;
  private PromptFactory factory;
  private ScreenManager screenManager;
  private ExecutionRegistry executionRegistry;
  private ExecutionCoordinator executionCoordinator;
  private List<String> dispatchedCommands;

  @BeforeEach
  void init() {
    dispatchedCommands = Collections.synchronizedList(new ArrayList<>());

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

    executionCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            executionRegistry,
            primaryDispatcher,
            immediateDispatcher,
            null,
            PostActionScheduler.forPlugin(plugin),
            null,
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

    org.mockito.Mockito.when(plugin.getEngine()).thenReturn(engine);
    org.mockito.Mockito.when(plugin.getScreenManager()).thenReturn(screenManager);
    org.mockito.Mockito.when(plugin.getExecutionRegistry()).thenReturn(executionRegistry);
    org.mockito.Mockito.when(plugin.getExecutionCoordinator()).thenReturn(executionCoordinator);
    org.mockito.Mockito.doCallRealMethod()
        .when(plugin)
        .onPlayerQuit(org.mockito.Mockito.any(PlayerQuitEvent.class));
    org.mockito.Mockito.doCallRealMethod().when(plugin).onDisable();

    server
        .getCommandMap()
        .register(
            "say",
            "minecraft",
            new Command("say") {
              @Override
              public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return true;
              }
            });
    server
        .getCommandMap()
        .register(
            "cmd",
            "minecraft",
            new Command("cmd") {
              @Override
              public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return true;
              }
            });
    server
        .getCommandMap()
        .register(
            "lifecyclelog",
            "minecraft",
            new Command("lifecyclelog") {
              @Override
              public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                dispatchedCommands.add(commandLabel + " " + String.join(" ", args));
                return true;
              }
            });
  }

  @Test
  @DisplayName(
      "Player quit with active prompt and delayed ON_CANCEL meta performs DISCARD_ONLY with zero execution registration")
  void playerQuitDiscardsActivePromptWithoutOnCancelExecution() {
    var player = createPlayer("QuitUser");
    screenManager.startSession(player, "/say test <p1> <!!:20lifecyclelog QuitCancelled>");
    assertTrue(engine.hasActiveSession(player));
    assertTrue(screenManager.hasActiveScreen(player));

    // Fire player quit event
    plugin.onPlayerQuit(
        new PlayerQuitEvent(
            player, Component.text("quit"), PlayerQuitEvent.QuitReason.DISCONNECTED));

    // State must be completely discarded
    assertFalse(engine.hasActiveSession(player));
    assertFalse(screenManager.hasActiveScreen(player));
    assertEquals(
        0, executionRegistry.size(), "No cancellation execution may be registered on quit");

    // Drain schedulers
    performTicks(40);

    assertEquals(
        0, dispatchedCommands.size(), "Zero cancel post-actions must be dispatched after quit");
    assertEquals(0, executionRegistry.size());
  }

  @Test
  @DisplayName(
      "Player quit with in-flight delayed post action cancels execution and leaves zero dangling tasks")
  void playerQuitWithInFlightDelayedExecutionCancelsCleanly() {
    var player = createPlayer("QuitWithDelayUser");
    screenManager.startSession(player, "/say done <p1> <!:20lifecyclelog DelayedAction>");
    assertTrue(engine.hasActiveSession(player));

    // Complete the prompt to enter delayed POST_ACTIONS stage
    screenManager.handleChatInput(player, "answer");
    performTicks(2);
    assertFalse(engine.hasActiveSession(player));
    assertEquals(
        1, executionRegistry.size(), "Execution must be registered for in-flight delayed action");

    // Advance 5 ticks (before delayed action at 20 ticks)
    performTicks(5);
    assertEquals(0, dispatchedCommands.size());

    // Player quits
    plugin.onPlayerQuit(
        new PlayerQuitEvent(
            player, Component.text("quit"), PlayerQuitEvent.QuitReason.DISCONNECTED));

    // Advance remaining ticks
    performTicks(35);

    assertEquals(0, dispatchedCommands.size(), "Delayed action must not dispatch after quit");
    assertEquals(0, executionRegistry.size(), "Registry must return to baseline");
  }

  @Test
  @DisplayName(
      "ReloadCommand teardown performs DISCARD_ONLY and leaves no scheduled cancellation tasks")
  void reloadCommandTeardownPerformsDiscardOnly() {
    var player1 = createPlayer("ReloadUser1");
    var player2 = createPlayer("ReloadUser2");

    // Player 1: active prompt with delayed ON_CANCEL
    screenManager.startSession(player1, "/say cmd <p1> <!!:20lifecyclelog OnCancelPcm>");
    assertTrue(engine.hasActiveSession(player1));

    // Player 2: completed prompt with in-flight delayed post action
    screenManager.startSession(player2, "/say cmd <p2> <!:20lifecyclelog DelayedPcm>");
    screenManager.handleChatInput(player2, "ans");
    performTicks(2);
    assertEquals(1, executionRegistry.size());

    var reloadCmd = new ReloadCommand(plugin);
    var sender = org.mockito.Mockito.mock(CommandSender.class);
    org.mockito.Mockito.when(sender.getName()).thenReturn("Admin");

    // Execute reload
    reloadCmd.executeReload(sender);

    // Perform ticks to drain player schedulers and global reload runnable
    performTicks(30);

    assertFalse(engine.hasActiveSession(player1));
    assertFalse(screenManager.hasActiveScreen(player1));
    assertEquals(
        0, executionRegistry.size(), "All active executions must be cancelled and removed");
    assertEquals(
        0,
        dispatchedCommands.size(),
        "Zero on-cancel or delayed actions may be dispatched during reload");
  }

  @Test
  @DisplayName(
      "Plugin disable teardown seam cancels all state with DISCARD_ONLY without running delayed cancel actions")
  void pluginDisableTeardownPerformsDiscardOnly() {
    var player = createPlayer("DisableUser");
    screenManager.startSession(player, "/say cmd <p1> <!!:15lifecyclelog OnDisableCancel>");
    assertTrue(engine.hasActiveSession(player));

    // Invoke plugin disable
    plugin.onDisable();

    assertFalse(engine.hasActiveSession(player));
    assertEquals(0, executionRegistry.size());

    // Drain schedulers
    performTicks(30);

    assertEquals(0, dispatchedCommands.size(), "Zero post-actions dispatched on disable");
    assertEquals(0, executionRegistry.size());
  }

  @Test
  @DisplayName("Provider teardown seam performs DISCARD_ONLY on custom screen sessions")
  void providerTeardownPerformsDiscardOnly() {
    var player = createPlayer("ProviderUser");
    var customHandle =
        new CustomScreenHandle() {
          @Override
          public long providerId() {
            return 100L;
          }

          @Override
          public String key() {
            return "custom_prov";
          }

          @Override
          public String ownerName() {
            return "DummyPlugin";
          }

          @Override
          public dev.cyr1en.promptpaper.custom.ProviderState state() {
            return dev.cyr1en.promptpaper.custom.ProviderState.ACTIVE;
          }
        };

    var dummyScreen = org.mockito.Mockito.mock(dev.cyr1en.promptui.InputScreen.class);
    var activeHandle =
        new ActiveScreenHandle(player.getUniqueId(), dummyScreen, 1L, 1L, 0L, 0, customHandle);

    screenManager.startSession(player, "/cmd <p1> <!!:10lifecyclelog CustomOnCancel>");
    assertTrue(engine.hasActiveSession(player));

    // Teardown provider
    screenManager.teardownCustomProvider(player, activeHandle);

    assertFalse(engine.hasActiveSession(player));
    assertEquals(0, executionRegistry.size());

    performTicks(20);

    assertEquals(0, dispatchedCommands.size());
    assertEquals(0, executionRegistry.size());
  }

  @Test
  @DisplayName(
      "Manual cancel control with USER_ACTIONS executes delayed on-cancel action owned by execution coordinator")
  void manualCancelExecutesDelayedOnCancelAction() {
    var player = createPlayer("ManualCancelUser");
    screenManager.startSession(player, "/say cmd <p1> <!!:10lifecyclelog ManualCancelDispatched>");
    assertTrue(engine.hasActiveSession(player));

    // User triggers manual cancel (e.g. /cmdp cancel)
    screenManager.cancelAll(player);

    assertFalse(engine.hasActiveSession(player));
    assertEquals(1, executionRegistry.size(), "Cancellation execution must be active during delay");

    // Advance 5 ticks -> not yet dispatched
    performTicks(5);
    assertEquals(0, dispatchedCommands.size());

    // Advance 6 more ticks (total 11) for timer and immediate action execution
    performTicks(6);
    assertEquals(1, dispatchedCommands.size());
    assertEquals("lifecyclelog ManualCancelDispatched", dispatchedCommands.get(0));

    // Execution completes and returns to baseline
    assertEquals(0, executionRegistry.size());
  }
}
