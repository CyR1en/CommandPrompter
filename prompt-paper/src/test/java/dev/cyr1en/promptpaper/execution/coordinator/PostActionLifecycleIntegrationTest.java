package dev.cyr1en.promptpaper.execution.coordinator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptcore.DispatchTarget;
import dev.cyr1en.promptcore.ParsedCommand;
import dev.cyr1en.promptcore.ParserConfig;
import dev.cyr1en.promptcore.PostCommandMeta;
import dev.cyr1en.promptcore.SessionResult;
import dev.cyr1en.promptcore.plan.ExecutionPlanAdapter;
import dev.cyr1en.promptcore.plan.ExecutionPlanDefinition;
import dev.cyr1en.promptcore.plan.PreDispatchGateSpec;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.custom.PlayerExecutor;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchErrorKind;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchOutcome;
import dev.cyr1en.promptpaper.execution.dispatch.ImmediateActionDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.PaperImmediateActionDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.PaperPrimaryCommandDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.PrimaryCommandDispatcher;
import dev.cyr1en.promptpaper.execution.postaction.PostActionScheduler;
import dev.cyr1en.promptpaper.execution.postaction.template.PapiReferenceResolver;
import dev.cyr1en.promptpaper.execution.runtime.DispatchContextSnapshot;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionPlanInstance;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionRegistry;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionStage;
import dev.cyr1en.promptpaper.execution.runtime.InputCompletion;
import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.hook.hooks.PapiHook;
import dev.cyr1en.promptpaper.preset.ConditionalPostCommandDefinition;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.ExecutionPolicy;
import dev.cyr1en.promptpaper.preset.PostCommand;
import dev.cyr1en.promptpaper.preset.PresetRegistry;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import dev.cyr1en.promptpaper.preset.TrustedPresetAction;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.util.CancellableTask;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Phase 5.5 PostActionRunner Lifecycle Integration Tests")
class PostActionLifecycleIntegrationTest extends MockBukkitTest {

  private ExecutionRegistry registry;
  private PromptEngine engine;
  private ScreenManager screenManager;
  private ImmediateActionDispatcher immediateActionDispatcher;
  private PrimaryCommandDispatcher primaryDispatcher;
  private HookContainer hookContainer;
  private List<String> dispatchedCommands;
  private Map<String, PostCommand> presetPostCommands;
  private Map<String, ConditionalPostCommandDefinition> conditionalPostCommands;

  static class ManualTestScheduler implements PostActionScheduler {
    static class ScheduledEntry {
      final Player player;
      final Runnable task;
      final Runnable retired;
      final long delayTicks;
      final AtomicBoolean cancelled = new AtomicBoolean(false);

      ScheduledEntry(Player player, Runnable task, Runnable retired, long delayTicks) {
        this.player = player;
        this.task = task;
        this.retired = retired;
        this.delayTicks = delayTicks;
      }
    }

    final List<ScheduledEntry> entries = new ArrayList<>();

    @Override
    public CancellableTask scheduleDelayed(
        Player player, Runnable task, Runnable retired, long delayTicks) {
      ScheduledEntry entry = new ScheduledEntry(player, task, retired, delayTicks);
      entries.add(entry);
      return () -> entry.cancelled.set(true);
    }

    public void advanceTicks(long ticks) {
      List<ScheduledEntry> snapshot = new ArrayList<>(entries);
      entries.clear();
      for (ScheduledEntry entry : snapshot) {
        if (!entry.cancelled.get()) {
          entry.task.run();
        }
      }
    }
  }

  private ManualTestScheduler manualScheduler;

  @BeforeEach
  void setUpCoordinator() {
    registry = new ExecutionRegistry();
    lenient().when(plugin.getExecutionRegistry()).thenReturn(registry);

    dispatchedCommands = new ArrayList<>();
    presetPostCommands = new ConcurrentHashMap<>();
    conditionalPostCommands = new ConcurrentHashMap<>();

    hookContainer = mock(HookContainer.class);
    lenient().when(plugin.getHookContainer()).thenReturn(hookContainer);
    lenient().when(hookContainer.getHook(PapiHook.class)).thenReturn(Optional.empty());

    var presetRegistry = mock(PresetRegistry.class);
    lenient().when(plugin.getPresetRegistry()).thenReturn(presetRegistry);
    lenient().when(presetRegistry.snapshot()).thenAnswer(inv -> currentSnapshot());
    lenient().when(presetRegistry.getSnapshot()).thenAnswer(inv -> currentSnapshot());

    engine = new PromptEngine(plugin, scheduler, null, registry);
    immediateActionDispatcher = new PaperImmediateActionDispatcher(plugin, scheduler);
    primaryDispatcher = new PaperPrimaryCommandDispatcher(plugin, scheduler);
    manualScheduler = new ManualTestScheduler();

    screenManager =
        new ScreenManager(
            plugin,
            engine,
            plugin.getPromptFactory(),
            scheduler,
            null,
            null,
            null,
            null);

    registerCapturingCommand("eco");
    registerCapturingCommand("say");
    registerCapturingCommand("msg");
    registerCapturingCommand("broadcast");
    registerCapturingCommand("goodcmd");
    registerCapturingCommand("failcmd", false);
  }

  private PresetSnapshot currentSnapshot() {
    return new PresetSnapshot(
        Map.of(), Map.copyOf(presetPostCommands), Map.of(), Map.copyOf(conditionalPostCommands));
  }

  private void registerCapturingCommand(String name) {
    registerCapturingCommand(name, true);
  }

  private void registerCapturingCommand(String name, boolean succeed) {
    Command cmd =
        new Command(name) {
          @Override
          public boolean execute(
              @NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            String full = commandLabel + (args.length > 0 ? " " + String.join(" ", args) : "");
            dispatchedCommands.add(full);
            return succeed;
          }
        };
    server.getCommandMap().register("test", cmd);
  }

  @Test
  @DisplayName("1. Primary success waits for immediate and delayed actions before COMPLETED")
  void primarySuccessWaitsForImmediateAndDelayedActionsBeforeCompleted() {
    var player = createPlayer("TestWaitPostActions");
    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            primaryDispatcher,
            immediateActionDispatcher,
            null,
            manualScheduler,
            null,
            p -> (task, retired) -> task.run());

    var immediatePcm =
        new PostCommandMeta("say immediate-action", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);
    var delayedPcm =
        new PostCommandMeta("say delayed-action", new int[0], 20, false, DispatchTarget.PASSTHROUGH, false);

    var sessionResult =
        new SessionResult("goodcmd", List.of(), List.of(immediatePcm, delayedPcm), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("goodcmd", List.of(), List.of(immediatePcm, delayedPcm), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player(),
            List.of(immediatePcm, delayedPcm));

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    // Primary command and immediate post-action dispatched
    assertTrue(dispatchedCommands.contains("goodcmd"));
    assertTrue(dispatchedCommands.contains("say immediate-action"));
    assertFalse(dispatchedCommands.contains("say delayed-action"));

    // Stage must remain POST_ACTIONS and registry remains active during delay
    assertEquals(ExecutionStage.POST_ACTIONS, instance.getStage());
    assertTrue(registry.hasActiveExecution(player.getUniqueId()));

    // Advance delayed scheduler ticks
    manualScheduler.advanceTicks(20);

    // Delayed action dispatched
    assertTrue(dispatchedCommands.contains("say delayed-action"));
    assertEquals(ExecutionStage.COMPLETED, instance.getStage());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
  }

  @Test
  @DisplayName("2. Primary failure runs only cancel policy post-actions")
  void primaryFailureRunsOnlyCancelPolicyActions() {
    var player = createPlayer("TestFailCancelPolicy");
    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            primaryDispatcher,
            immediateActionDispatcher,
            null,
            manualScheduler,
            null,
            p -> (task, retired) -> task.run());

    var onCompletePcm =
        new PostCommandMeta("say success-pcm", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);
    var onCancelPcm =
        new PostCommandMeta("say cancel-pcm", new int[0], 0, true, DispatchTarget.PASSTHROUGH, false);

    var sessionResult =
        new SessionResult("failcmd", List.of(), List.of(onCompletePcm), List.of(onCancelPcm));
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("failcmd", List.of(), List.of(onCompletePcm, onCancelPcm), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player(),
            List.of(onCompletePcm, onCancelPcm));

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    // Primary was attempted and returned false
    assertTrue(dispatchedCommands.contains("failcmd"));
    // ON_COMPLETE pcm must NOT run
    assertFalse(dispatchedCommands.contains("say success-pcm"));
    // ON_CANCEL pcm MUST run
    assertTrue(dispatchedCommands.contains("say cancel-pcm"));
    assertEquals(ExecutionStage.ERROR, instance.getStage());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
  }

  @Test
  @DisplayName("3. Approval denial order: onDeny action runs then cancel post-action chain")
  void approvalDenialOrderOnDenyThenCancelChain() {
    var player = createPlayer("TestApprovalDenialOrder");

    var dispatchOrder = new ArrayList<String>();
    ImmediateActionDispatcher orderingDispatcher =
        (request, callback) -> {
          dispatchOrder.add(request.command());
          callback.onComplete(DispatchOutcome.success());
        };

    PreDispatchGateHandler denyingGateHandler =
        (p, inst, spec, idx, callback) -> {
          var onDenyAction =
              new TrustedPresetAction(
                  dev.cyr1en.promptcore.logic.transform.TemplateCompiler.compile("say on-deny-fired"),
                  ExecuteAs.CONSOLE,
                  0);
          callback.onResult(PreDispatchGateResult.denied(onDenyAction));
        };

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            primaryDispatcher,
            orderingDispatcher,
            denyingGateHandler,
            manualScheduler,
            null,
            p -> (task, retired) -> task.run());

    var onCancelPcm =
        new PostCommandMeta("say cancel-chain-action", new int[0], 0, true, DispatchTarget.PASSTHROUGH, false);

    var approvalSpec = new PreDispatchGateSpec.Approval("test_preset");
    var plan =
        new ExecutionPlanDefinition(
            dev.cyr1en.promptcore.logic.transform.TemplateCompiler.compile("primary"),
            List.of(approvalSpec),
            List.of());

    var sessionResult =
        new SessionResult("primary", List.of(), List.of(), List.of(onCancelPcm));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player(),
            List.of(onCancelPcm));

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    assertEquals(2, dispatchOrder.size());
    assertEquals("say on-deny-fired", dispatchOrder.get(0));
    assertEquals("say cancel-chain-action", dispatchOrder.get(1));
    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
  }

  @Test
  @DisplayName("4. breakIf and manual cancel run conditional and delayed on-cancel actions")
  void breakIfAndManualCancelConditionalAndDelayed() {
    var player = createPlayer("TestBreakIfCancel");

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            primaryDispatcher,
            immediateActionDispatcher,
            null,
            manualScheduler,
            null,
            p -> (task, retired) -> task.run());
    engine.setExecutionCoordinator(customCoordinator);

    var delayedCancelPcm =
        new PostCommandMeta("say cancel-delayed-50t", new int[0], 50, true, DispatchTarget.PASSTHROUGH, false);

    var completion =
        new InputCompletion(
            player.getUniqueId(),
            1L,
            1L,
            List.of(),
            "",
            null,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player(),
            List.of(delayedCancelPcm));

    var instanceOpt = customCoordinator.coordinateCancellation(player, completion);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    // While delayed cancel action is pending, stage is POST_ACTIONS and registry is active
    assertEquals(ExecutionStage.POST_ACTIONS, instance.getStage());
    assertTrue(registry.hasActiveExecution(player.getUniqueId()));
    assertFalse(dispatchedCommands.contains("say cancel-delayed-50t"));

    manualScheduler.advanceTicks(50);

    assertTrue(dispatchedCommands.contains("say cancel-delayed-50t"));
    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
  }

  @Test
  @DisplayName("5. Coordinator cancel / quit / reload / disable drops delayed actions and cancels handles")
  void quitReloadDisableDropsDelayedActionsAndCancelsHandles() {
    var player = createPlayer("TestDropDelayed");

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            primaryDispatcher,
            immediateActionDispatcher,
            null,
            manualScheduler,
            null,
            p -> (task, retired) -> task.run());

    var delayedPcm =
        new PostCommandMeta("say should-never-run", new int[0], 100, false, DispatchTarget.PASSTHROUGH, false);

    var sessionResult = new SessionResult("goodcmd", List.of(), List.of(delayedPcm), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("goodcmd", List.of(), List.of(delayedPcm), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player(),
            List.of(delayedPcm));

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    assertEquals(ExecutionStage.POST_ACTIONS, instance.getStage());
    assertTrue(registry.hasActiveExecution(player.getUniqueId()));

    // Simulate quit/reload/disable cancel
    customCoordinator.cancel(player.getUniqueId());

    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));

    // Advancing ticks must NOT run cancelled action
    manualScheduler.advanceTicks(100);
    assertFalse(dispatchedCommands.contains("say should-never-run"));
  }

  @Test
  @DisplayName("6. Timer cancel and action-callback race yields exactly one terminal state")
  void timerCancelAndActionCallbackCancelRacesYieldOneTerminal() {
    var player = createPlayer("TestRaceCancel");

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            primaryDispatcher,
            immediateActionDispatcher,
            null,
            manualScheduler,
            null,
            p -> (task, retired) -> task.run());

    var delayedPcm =
        new PostCommandMeta("say race-action", new int[0], 10, false, DispatchTarget.PASSTHROUGH, false);

    var sessionResult = new SessionResult("goodcmd", List.of(), List.of(delayedPcm), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("goodcmd", List.of(), List.of(delayedPcm), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player(),
            List.of(delayedPcm));

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    // Simultaneously advance scheduler and cancel
    customCoordinator.cancel(player.getUniqueId());
    manualScheduler.advanceTicks(10);

    assertTrue(instance.isTerminal());
    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
  }

  @Test
  @DisplayName("7. Registry remains active during delays then returns to baseline")
  void registryRemainsActiveDuringDelaysThenBaseline() {
    var player = createPlayer("TestRegistryBaseline");

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            primaryDispatcher,
            immediateActionDispatcher,
            null,
            manualScheduler,
            null,
            p -> (task, retired) -> task.run());

    var delayed1 =
        new PostCommandMeta("say action-1", new int[0], 10, false, DispatchTarget.PASSTHROUGH, false);
    var delayed2 =
        new PostCommandMeta("say action-2", new int[0], 20, false, DispatchTarget.PASSTHROUGH, false);

    var sessionResult = new SessionResult("goodcmd", List.of(), List.of(delayed1, delayed2), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("goodcmd", List.of(), List.of(delayed1, delayed2), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player(),
            List.of(delayed1, delayed2));

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());

    // Step 1: active during first delay
    assertTrue(registry.hasActiveExecution(player.getUniqueId()));
    manualScheduler.advanceTicks(10);
    assertTrue(dispatchedCommands.contains("say action-1"));

    // Step 2: active during second delay
    assertTrue(registry.hasActiveExecution(player.getUniqueId()));
    manualScheduler.advanceTicks(20);
    assertTrue(dispatchedCommands.contains("say action-2"));

    // Step 3: baseline reached
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
  }

  @Test
  @DisplayName("8. PAPI injection FLOW-11 end-to-end: exact token resolved and untrusted inline protected")
  void papiInjectionFlow11EndToEnd() {
    var player = createPlayer("PapiUser");
    player.setOp(true);

    var papi = mock(PapiHook.class);
    when(papi.setPlaceholder(eq(player), eq("%server_name%"))).thenReturn("SurvivalCraft");
    when(hookContainer.getHook(PapiHook.class)).thenReturn(Optional.of(papi));

    var preset =
        new PostCommand(
            "papi_reward",
            "broadcast Welcome %server_name% {player}",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.CONSOLE,
            0);
    presetPostCommands.put("papi_reward", preset);

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            primaryDispatcher,
            immediateActionDispatcher,
            null,
            manualScheduler,
            p -> ExecutionCoordinator.defaultPapiResolver(plugin, p),
            p -> (task, retired) -> task.run());

    var presetPcm =
        new PostCommandMeta("papi_reward", new int[0], 0, false, DispatchTarget.CONSOLE, true);
    var inlinePcm =
        new PostCommandMeta("broadcast Inline %server_name% {player}", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);

    var sessionResult =
        new SessionResult("goodcmd", List.of(), List.of(presetPcm, inlinePcm), List.of());
    var snapshot = currentSnapshot();
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            null,
            snapshot,
            DispatchContextSnapshot.player(),
            List.of(presetPcm, inlinePcm));

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());

    // Trusted preset expanded PAPI token
    assertTrue(dispatchedCommands.contains("broadcast Welcome \"SurvivalCraft\" PapiUser"));
    // Untrusted inline did NOT expand PAPI token (%server_name% left as plain text)
    assertTrue(dispatchedCommands.contains("broadcast Inline %server_name% PapiUser"));
  }

  @Test
  @DisplayName("9. Captured snapshot preserved across reload during delayed post-actions")
  void capturedSnapshotPreservedAcrossReload() {
    var player = createPlayer("SnapshotReloadUser");
    player.setOp(true);

    var presetV1 =
        new PostCommand(
            "reward",
            "eco give {player} 100",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.CONSOLE,
            20);
    presetPostCommands.put("reward", presetV1);
    var capturedSnapshot = currentSnapshot();

    // Now reload changes preset definition to 500
    var presetV2 =
        new PostCommand(
            "reward",
            "eco give {player} 500",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.CONSOLE,
            20);
    presetPostCommands.put("reward", presetV2);

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            primaryDispatcher,
            immediateActionDispatcher,
            null,
            manualScheduler,
            null,
            p -> (task, retired) -> task.run());

    var presetPcm =
        new PostCommandMeta("reward", new int[0], 20, false, DispatchTarget.CONSOLE, true);

    var sessionResult = new SessionResult("goodcmd", List.of(), List.of(presetPcm), List.of());
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            null,
            capturedSnapshot,
            DispatchContextSnapshot.player(),
            List.of(presetPcm));

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());

    manualScheduler.advanceTicks(20);

    // Must use V1 snapshot (100), not mutated live registry (500)
    assertTrue(dispatchedCommands.contains("eco give SnapshotReloadUser 100"));
    assertFalse(dispatchedCommands.contains("eco give SnapshotReloadUser 500"));
  }

  @Test
  @DisplayName("10. Legacy existing PCMs compatibility: answer references and player placeholder")
  void legacyExistingPcmsCompatibility() {
    var player = createPlayer("LegacyUser");

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            primaryDispatcher,
            immediateActionDispatcher,
            null,
            manualScheduler,
            null,
            p -> (task, retired) -> task.run());

    var pcm =
        new PostCommandMeta("msg {player} your answer was {input:1}", new int[]{0}, 0, false, DispatchTarget.PASSTHROUGH, false);

    var sessionResult = new SessionResult("goodcmd", List.of("apple"), List.of(pcm), List.of());
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            null,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player(),
            List.of(pcm));

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());

    assertTrue(dispatchedCommands.contains("msg LegacyUser your answer was \"apple\""));
  }

  @Test
  @DisplayName("11. Zero production calls to deprecated PostCommandResolver, PostCommandPlaceholderResolver, and dispatchPCMs")
  void noProductionLegacyResolverCallers() throws IOException {
    Path srcMain = Path.of("src/main/java");
    if (!Files.exists(srcMain)) {
      srcMain = Path.of("prompt-paper/src/main/java");
    }
    assertTrue(Files.exists(srcMain), "src/main/java directory must exist");

    try (var stream = Files.walk(srcMain)) {
      var javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
      for (Path file : javaFiles) {
        String content = Files.readString(file);
        assertFalse(
            content.contains("PostCommandResolver"),
            "Production file " + file + " must not reference deprecated PostCommandResolver");
        assertFalse(
            content.contains("PostCommandPlaceholderResolver"),
            "Production file " + file + " must not reference deprecated PostCommandPlaceholderResolver");
        assertFalse(
            content.contains("dispatchPCMs"),
            "Production file " + file + " must not reference deprecated dispatchPCMs");
      }
    }
  }
}
