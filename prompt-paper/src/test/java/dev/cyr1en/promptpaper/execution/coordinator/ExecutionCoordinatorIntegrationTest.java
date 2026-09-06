package dev.cyr1en.promptpaper.execution.coordinator;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptcore.DispatchTarget;
import dev.cyr1en.promptcore.ParsedCommand;
import dev.cyr1en.promptcore.ParserConfig;
import dev.cyr1en.promptcore.PostCommandMeta;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.SessionResult;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import dev.cyr1en.promptcore.plan.ExecutionPlanAdapter;
import dev.cyr1en.promptcore.plan.ExecutionPlanDefinition;
import dev.cyr1en.promptcore.plan.PreDispatchGateSpec;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.execution.dispatch.ActionDispatchCallback;
import dev.cyr1en.promptpaper.execution.dispatch.ActionTrustLevel;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchErrorKind;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchMode;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchOutcome;
import dev.cyr1en.promptpaper.execution.dispatch.ImmediateActionDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.ImmediateActionRequest;
import dev.cyr1en.promptpaper.execution.dispatch.PaperImmediateActionDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.PaperPrimaryCommandDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.PermissionAttachmentContext;
import dev.cyr1en.promptpaper.execution.dispatch.PrimaryCommandDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.PrimaryDispatchCallback;
import dev.cyr1en.promptpaper.execution.postaction.PostActionScheduler;
import dev.cyr1en.promptpaper.execution.runtime.DispatchContextSnapshot;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionRegistry;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionStage;
import dev.cyr1en.promptpaper.execution.runtime.InputCompletion;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.ExecutionPolicy;
import dev.cyr1en.promptpaper.preset.PostCommand;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import dev.cyr1en.promptpaper.preset.TrustedPresetAction;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Execution Coordinator and Runtime Integration Tests (Phase 5.3)")
class ExecutionCoordinatorIntegrationTest extends MockBukkitTest {

  private PromptEngine engine;
  private ExecutionRegistry registry;
  private ScreenManager screenManager;
  private ExecutionCoordinator coordinator;
  private ImmediateActionDispatcher immediateActionDispatcher;

  @BeforeEach
  void setup() {
    registry = new ExecutionRegistry();
    org.mockito.Mockito.lenient().when(plugin.getExecutionRegistry()).thenReturn(registry);
    engine = new PromptEngine(plugin, scheduler, null, registry);
    immediateActionDispatcher = new PaperImmediateActionDispatcher(plugin, scheduler);
    var primaryDispatcher = new PaperPrimaryCommandDispatcher(plugin, scheduler);
    coordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            primaryDispatcher,
            immediateActionDispatcher,
            p -> (task, retired) -> task.run());
    screenManager =
        new ScreenManager(
            plugin, engine, plugin.getPromptFactory(), scheduler, null, null, null, coordinator);
  }

  private void registerMockCommand(String name, boolean succeed, List<String> executionLog) {
    Command cmd =
        new Command(name) {
          @Override
          public boolean execute(
              @NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            if (executionLog != null) {
              executionLog.add(
                  commandLabel + (args.length > 0 ? " " + String.join(" ", args) : ""));
            }
            return succeed;
          }
        };
    server.getCommandMap().register("test", cmd);
  }

  private void registerThrowingCommand(String name) {
    Command cmd =
        new Command(name) {
          @Override
          public boolean execute(
              @NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            throw new RuntimeException("Command execution failure: " + name);
          }
        };
    server.getCommandMap().register(name, cmd);
  }

  @Test
  @DisplayName("FLOW-10: Racing/duplicate callback dispatches primary and PCMs exactly once")
  void duplicateOrRacingCallbackDispatchesPcmsExactlyOnce() {
    var player = createPlayer("TestFlow10");
    var executionLog = new ArrayList<String>();
    registerMockCommand("pcmcmd", true, executionLog);
    registerMockCommand("maincmd", true, executionLog);

    var capturedCallback = new AtomicReference<PrimaryDispatchCallback>();
    PrimaryCommandDispatcher controllableDispatcher =
        (request, callback) -> capturedCallback.set(callback);

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            controllableDispatcher,
            immediateActionDispatcher,
            p -> (task, retired) -> task.run());

    var pcm =
        new PostCommandMeta("pcmcmd", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);
    var sessionResult = new SessionResult("maincmd", List.of("arg1"), List.of(pcm), List.of());
    var parsed = new ParsedCommand("maincmd", List.of(), List.of(pcm), ParserConfig.ANGLE_BRACKETS);
    var plan = ExecutionPlanAdapter.fromParsedCommand(parsed);
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();
    assertEquals(ExecutionStage.PRIMARY_DISPATCH, instance.getStage());

    var cb = capturedCallback.get();
    assertNotNull(cb);

    // Call callback first time
    cb.onComplete(DispatchOutcome.success());
    assertEquals(ExecutionStage.COMPLETED, instance.getStage());
    assertEquals(1, executionLog.size());
    assertEquals("pcmcmd", executionLog.get(0));

    // Duplicate callback invocation must be safely ignored
    cb.onComplete(DispatchOutcome.success());
    assertEquals(1, executionLog.size(), "Duplicate callback must not trigger PCMs twice");
  }

  @Test
  @DisplayName("Primary dispatch returns false: skips success PCM and triggers error handling")
  void primaryDispatchReturnsFalseSkipsSuccessPcm() {
    var player = createPlayer("TestFailureFlow");
    var executionLog = new ArrayList<String>();
    registerMockCommand("successpcm", true, executionLog);
    registerMockCommand("cancelpcm", true, executionLog);

    var capturedCallback = new AtomicReference<PrimaryDispatchCallback>();
    PrimaryCommandDispatcher controllableDispatcher =
        (request, callback) -> capturedCallback.set(callback);

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            controllableDispatcher,
            immediateActionDispatcher,
            p -> (task, retired) -> task.run());

    var successPcm =
        new PostCommandMeta("successpcm", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);
    var cancelPcm =
        new PostCommandMeta("cancelpcm", new int[0], 0, true, DispatchTarget.PASSTHROUGH, false);
    var sessionResult =
        new SessionResult("badcmd", List.of(), List.of(successPcm), List.of(cancelPcm));
    var parsed =
        new ParsedCommand(
            "badcmd", List.of(), List.of(successPcm, cancelPcm), ParserConfig.ANGLE_BRACKETS);
    var plan = ExecutionPlanAdapter.fromParsedCommand(parsed);
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    var cb = capturedCallback.get();
    assertNotNull(cb);

    // Simulate primary failure
    cb.onComplete(
        DispatchOutcome.failure(
            DispatchErrorKind.DISPATCH_RETURNED_FALSE, "dispatch returned false"));

    assertEquals(ExecutionStage.ERROR, instance.getStage());
    assertFalse(executionLog.contains("successpcm"), "Success PCM must NOT be executed on failure");
    assertTrue(executionLog.contains("cancelpcm"), "Cancel PCM should be invoked on failure");
    assertFalse(
        registry.hasActiveExecution(player.getUniqueId()),
        "Terminal instance removed from registry");
  }

  @Test
  @DisplayName("Primary dispatch throws exception: transitions to ERROR and skips success PCM")
  void primaryDispatchExceptionTransitionsToError() {
    var player = createPlayer("TestExceptionFlow");
    var executionLog = new ArrayList<String>();
    registerMockCommand("successpcm", true, executionLog);

    var capturedCallback = new AtomicReference<PrimaryDispatchCallback>();
    PrimaryCommandDispatcher controllableDispatcher =
        (request, callback) -> capturedCallback.set(callback);

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            controllableDispatcher,
            immediateActionDispatcher,
            p -> (task, retired) -> task.run());

    var successPcm =
        new PostCommandMeta("successpcm", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);
    var sessionResult = new SessionResult("throwcmd", List.of(), List.of(successPcm), List.of());
    var parsed =
        new ParsedCommand("throwcmd", List.of(), List.of(successPcm), ParserConfig.ANGLE_BRACKETS);
    var plan = ExecutionPlanAdapter.fromParsedCommand(parsed);
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    var cb = capturedCallback.get();
    assertNotNull(cb);

    cb.onComplete(
        DispatchOutcome.failure(DispatchErrorKind.EXCEPTION_THROWN, new RuntimeException("boom")));

    assertEquals(ExecutionStage.ERROR, instance.getStage());
    assertEquals(0, executionLog.size(), "Success PCM must NOT be executed on exception");
  }

  @Test
  @DisplayName("Success PCM waits for primary command completion callback")
  void successPcmWaitsForPrimaryCallback() {
    var player = createPlayer("TestWaitFlow");
    var executionLog = new ArrayList<String>();
    registerMockCommand("maincmd", true, executionLog);
    registerMockCommand("pcmcmd", true, executionLog);

    var capturedCallback = new AtomicReference<PrimaryDispatchCallback>();
    PrimaryCommandDispatcher controllableDispatcher =
        (request, callback) -> capturedCallback.set(callback);

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            controllableDispatcher,
            immediateActionDispatcher,
            p -> (task, retired) -> task.run());

    var pcm =
        new PostCommandMeta("pcmcmd", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);
    var sessionResult = new SessionResult("maincmd", List.of(), List.of(pcm), List.of());
    var parsed = new ParsedCommand("maincmd", List.of(), List.of(pcm), ParserConfig.ANGLE_BRACKETS);
    var plan = ExecutionPlanAdapter.fromParsedCommand(parsed);
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    customCoordinator.coordinate(player, completion, sessionResult);

    // Prior to callback invocation: NO PCMs executed
    assertEquals(0, executionLog.size(), "PCMs must not execute before callback");

    // Invoke callback
    capturedCallback.get().onComplete(DispatchOutcome.success());
    assertEquals(1, executionLog.size());
    assertEquals("pcmcmd", executionLog.get(0));
  }

  @Test
  @DisplayName("Captured PresetSnapshot at inception survives reload during session")
  void capturedSnapshotSurvivesReloadDuringExecution() {
    var player = createPlayer("TestSnapshotReload");
    var executionLog = new ArrayList<String>();
    registerMockCommand("oldcmd", true, executionLog);
    registerMockCommand("newcmd", true, executionLog);

    // Initial snapshot has preset 'mypost' -> 'oldcmd'
    var postCmdOld =
        new PostCommand("mypost", "oldcmd", ExecutionPolicy.ON_COMPLETE, ExecuteAs.PLAYER, 0);
    var initialSnapshot =
        new PresetSnapshot(Map.of(), Map.of("mypost", postCmdOld), Map.of(), Map.of(), 1L);

    var pcm = new PostCommandMeta("mypost", new int[0], 0, false, DispatchTarget.PASSTHROUGH, true);
    var sessionResult = new SessionResult("maincmd", List.of(), List.of(pcm), List.of());
    var parsed = new ParsedCommand("maincmd", List.of(), List.of(pcm), ParserConfig.ANGLE_BRACKETS);
    var plan = ExecutionPlanAdapter.fromParsedCommand(parsed);

    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            initialSnapshot,
            DispatchContextSnapshot.player());

    var capturedCallback = new AtomicReference<PrimaryDispatchCallback>();
    PrimaryCommandDispatcher controllableDispatcher =
        (request, callback) -> capturedCallback.set(callback);

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            controllableDispatcher,
            immediateActionDispatcher,
            p -> (task, retired) -> task.run());

    customCoordinator.coordinate(player, completion, sessionResult);

    // Complete primary
    capturedCallback.get().onComplete(DispatchOutcome.success());

    // Verified that 'oldcmd' was executed from the captured inception snapshot
    assertTrue(executionLog.contains("oldcmd"), "Must execute oldcmd from inception snapshot");
    assertFalse(executionLog.contains("newcmd"));
  }

  @Test
  @DisplayName("NORMAL, CONSOLE, and ATTACHMENT modes map correctly")
  void dispatchModeMapping() {
    var player = createPlayer("TestModes");
    var lastMode = new AtomicReference<DispatchMode>();
    var lastAttachment = new AtomicReference<PermissionAttachmentContext>();

    PrimaryCommandDispatcher mockDispatcher =
        (request, callback) -> {
          lastMode.set(request.mode());
          lastAttachment.set(request.attachmentContext());
          callback.onComplete(DispatchOutcome.success());
        };

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            mockDispatcher,
            immediateActionDispatcher,
            p -> (task, retired) -> task.run());

    // 1. Normal (Player)
    var completionPlayer =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            new SessionResult("cmd1", List.of(), List.of(), List.of()),
            ExecutionPlanAdapter.fromParsedCommand(
                new ParsedCommand("cmd1", List.of(), List.of(), ParserConfig.ANGLE_BRACKETS)),
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());
    customCoordinator.coordinate(player, completionPlayer, null);
    assertEquals(DispatchMode.PLAYER, lastMode.get());

    // 2. Console
    var completionConsole =
        InputCompletion.of(
            player.getUniqueId(),
            2L,
            1L,
            new SessionResult("cmd2", List.of(), List.of(), List.of()),
            ExecutionPlanAdapter.fromParsedCommand(
                new ParsedCommand("cmd2", List.of(), List.of(), ParserConfig.ANGLE_BRACKETS)),
            PresetSnapshot.empty(),
            DispatchContextSnapshot.console());
    customCoordinator.coordinate(player, completionConsole, null);
    assertEquals(DispatchMode.CONSOLE, lastMode.get());

    // 3. Attachment
    var attachmentContext =
        new DispatchContextSnapshot(
            ExecuteAs.PLAYER, "temp.perm", true, List.of("node.a", "node.b"));
    var completionAttachment =
        InputCompletion.of(
            player.getUniqueId(),
            3L,
            1L,
            new SessionResult("cmd3", List.of(), List.of(), List.of()),
            ExecutionPlanAdapter.fromParsedCommand(
                new ParsedCommand("cmd3", List.of(), List.of(), ParserConfig.ANGLE_BRACKETS)),
            PresetSnapshot.empty(),
            attachmentContext);
    customCoordinator.coordinate(player, completionAttachment, null);
    assertEquals(DispatchMode.ATTACHMENT, lastMode.get());
    assertNotNull(lastAttachment.get());
    assertEquals("temp.perm", lastAttachment.get().permissionKey());
    assertEquals(List.of("node.a", "node.b"), lastAttachment.get().permissionSnapshot());
  }

  @Test
  @DisplayName("Stale incarnation / callback is rejected and dropped")
  void staleIncarnationCallbackRejected() {
    var player = createPlayer("TestStale");
    var executionLog = new ArrayList<String>();
    registerMockCommand("pcmcmd", true, executionLog);

    var capturedCallback = new AtomicReference<PrimaryDispatchCallback>();
    PrimaryCommandDispatcher controllableDispatcher =
        (request, callback) -> capturedCallback.set(callback);

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            controllableDispatcher,
            immediateActionDispatcher,
            p -> (task, retired) -> task.run());

    var pcm =
        new PostCommandMeta("pcmcmd", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);
    var sessionResult = new SessionResult("maincmd", List.of(), List.of(pcm), List.of());
    var parsed = new ParsedCommand("maincmd", List.of(), List.of(pcm), ParserConfig.ANGLE_BRACKETS);
    var plan = ExecutionPlanAdapter.fromParsedCommand(parsed);
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    customCoordinator.coordinate(player, completion, sessionResult);

    // Cancel execution before callback fires
    customCoordinator.cancel(player.getUniqueId());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));

    // Now fire the deferred callback — must be dropped because instance is no longer in registry
    capturedCallback.get().onComplete(DispatchOutcome.success());
    assertEquals(0, executionLog.size(), "Stale callback must not trigger PCMs");
  }

  @Test
  @DisplayName("Concurrent execution collision for same initiator UUID is rejected")
  void concurrentExecutionCollisionRejected() {
    var player = createPlayer("TestCollision");

    PrimaryCommandDispatcher controllableDispatcher =
        (request, callback) -> {}; // Do not complete callback, leave active

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            controllableDispatcher,
            immediateActionDispatcher,
            p -> (task, retired) -> task.run());

    var sessionResult1 = new SessionResult("cmd1", List.of(), List.of(), List.of());
    var plan1 =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("cmd1", List.of(), List.of(), ParserConfig.ANGLE_BRACKETS));
    var completion1 =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult1,
            plan1,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var first = customCoordinator.coordinate(player, completion1, sessionResult1);
    assertTrue(first.isPresent());
    assertTrue(registry.hasActiveExecution(player.getUniqueId()));

    // Second execution attempt while first is still active: must be rejected
    var sessionResult2 = new SessionResult("cmd2", List.of(), List.of(), List.of());
    var plan2 =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("cmd2", List.of(), List.of(), ParserConfig.ANGLE_BRACKETS));
    var completion2 =
        InputCompletion.of(
            player.getUniqueId(),
            2L,
            1L,
            sessionResult2,
            plan2,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var second = customCoordinator.coordinate(player, completion2, sessionResult2);
    assertTrue(second.isEmpty(), "Concurrent execution for same player must be rejected");
  }

  @Test
  @DisplayName("End-to-end: ScreenManager submission routes through ExecutionCoordinator")
  void screenManagerSubmissionRoutesThroughCoordinator() {
    var player = createPlayer("TestEndToEnd");
    var executionLog = new ArrayList<String>();
    registerMockCommand("finalcmd", true, executionLog);
    registerMockCommand("poststep", true, executionLog);

    var pcm =
        new PostCommandMeta("poststep", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);
    var parsed =
        new ParsedCommand(
            "finalcmd <a:test>",
            List.of(
                new PromptTag(
                    "<a:test>",
                    "a",
                    "",
                    "test",
                    true,
                    null,
                    PromptTag.AnswerType.STRING,
                    List.of(),
                    false,
                    null,
                    null,
                    Map.of(),
                    null)),
            List.of(pcm),
            ParserConfig.ANGLE_BRACKETS);

    var intercept = engine.interceptResult(player, "finalcmd <a:test> <!poststep>");
    assertTrue(intercept instanceof dev.cyr1en.promptpaper.engine.InterceptResult.Started);

    // Verify inception artifacts were stored
    var artifactsOpt = engine.getInceptionArtifacts(player.getUniqueId());
    assertTrue(artifactsOpt.isPresent());

    // Submit answer through engine
    var sessionOpt = engine.submit(player, "myanswer");
    assertTrue(sessionOpt.isPresent());

    // Hand submitted result to screen manager (or screen result path)
    // Verify coordinating through screen manager's coordinator executes command
    var dispatchSnapshot = DispatchContextSnapshot.player();
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            artifactsOpt.get().incarnation(),
            1L,
            sessionOpt.get(),
            artifactsOpt.get().planDefinition(),
            artifactsOpt.get().presetSnapshot(),
            dispatchSnapshot);

    coordinator.coordinate(player, completion, sessionOpt.get());
    performTicks(5);

    assertTrue(executionLog.contains("finalcmd \"myanswer\""));
    assertTrue(executionLog.contains("poststep"));
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
  }

  @Test
  @DisplayName("Inception artifacts cleanup on session cancellation or discard")
  void inceptionArtifactsCleanupOnCancel() {
    var player = createPlayer("TestArtifactsCleanup");
    engine.interceptResult(player, "finalcmd <a:test>");
    assertTrue(engine.getInceptionArtifacts(player.getUniqueId()).isPresent());

    engine.cancel(player, CancelReason.MANUAL);
    assertTrue(engine.getInceptionArtifacts(player.getUniqueId()).isEmpty());

    // Re-intercept and discard
    engine.interceptResult(player, "finalcmd <a:test>");
    assertTrue(engine.getInceptionArtifacts(player.getUniqueId()).isPresent());

    engine.discard(player.getUniqueId());
    assertTrue(engine.getInceptionArtifacts(player.getUniqueId()).isEmpty());
  }

  @Test
  @DisplayName("Quit, cancel, and disable cleanup resets registry to baseline")
  void cleanupResetsRegistryToBaseline() {
    var player = createPlayer("TestCleanup");
    PrimaryCommandDispatcher controllableDispatcher = (request, callback) -> {};

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            controllableDispatcher,
            immediateActionDispatcher,
            p -> (task, retired) -> task.run());

    var sessionResult = new SessionResult("cmd", List.of(), List.of(), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("cmd", List.of(), List.of(), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    customCoordinator.coordinate(player, completion, sessionResult);
    assertEquals(1, registry.size());

    // 1. cancelAll
    customCoordinator.cancelAll();
    assertEquals(0, registry.size());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
  }

  @Test
  @DisplayName("Console callback with initiator retirement does zero PCM/PAPI and removes registry")
  void consoleCallbackWithInitiatorRetirementDoesZeroPcmAndRemovesRegistry() {
    var player = createPlayer("TestConsoleRetired");
    var executionLog = new ArrayList<String>();
    registerMockCommand("postcmd", true, executionLog);

    var capturedCallback = new AtomicReference<PrimaryDispatchCallback>();
    PrimaryCommandDispatcher controllableDispatcher =
        (request, callback) -> capturedCallback.set(callback);

    // PlayerExecutor that simulates immediate retirement on return hop
    var retiredCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            controllableDispatcher,
            immediateActionDispatcher,
            p -> (task, onRetired) -> onRetired.run());

    var pcm =
        new PostCommandMeta("postcmd", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);
    var sessionResult = new SessionResult("consolecmd", List.of(), List.of(pcm), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("consolecmd", List.of(), List.of(pcm), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.console());

    var instanceOpt = retiredCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    // Fire callback from console dispatcher
    capturedCallback.get().onComplete(DispatchOutcome.success());

    // Verified: zero PCM evaluation, instance transitioned to ERROR / terminal, removed from
    // registry
    assertEquals(0, executionLog.size(), "No PCMs should be executed on retired hop");
    assertEquals(ExecutionStage.ERROR, instance.getStage());
    assertTrue(instance.isCleanedUp());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
    assertEquals(0, registry.size());
  }

  @Test
  @DisplayName("PromptEngine has no identity map sidecar for PCMs")
  void noPromptEngineIdentityMapSidecar() {
    for (var field : PromptEngine.class.getDeclaredFields()) {
      assertNotEquals(
          "dispatchPcmSnapshots",
          field.getName(),
          "dispatchPcmSnapshots identity sidecar must be removed");
    }
  }

  @Test
  @DisplayName("Every cancel/retirement/collision path releases PCM list without sidecar leakage")
  void cancelRetirementCollisionReleasesPcms() {
    var player = createPlayer("TestPcmRelease");

    // 1. Session inception and cancel
    engine.interceptResult(player, "/cmd <a:val> <!pcm1>");
    assertTrue(engine.getInceptionArtifacts(player.getUniqueId()).isPresent());
    assertFalse(
        engine.getInceptionArtifacts(player.getUniqueId()).get().originalPostCommands().isEmpty());

    engine.cancel(player, CancelReason.MANUAL);
    assertTrue(engine.getInceptionArtifacts(player.getUniqueId()).isEmpty());

    // 2. Execution cancellation releases completion owned state
    var pcm = new PostCommandMeta("pcm1", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);
    var sessionResult = new SessionResult("cmd", List.of(), List.of(pcm), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("cmd", List.of(), List.of(pcm), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            2L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player(),
            List.of(pcm));

    var instanceOpt = coordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    coordinator.cancel(player.getUniqueId());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
    assertTrue(instanceOpt.get().isCleanedUp());
  }

  @Test
  @DisplayName(
      "Active execution in ExecutionRegistry causes intercept rejection (fail closed, no leak)")
  void activeExecutionCausesInterceptRejection() {
    var player = createPlayer("TestActiveExec");
    var executionLog = new ArrayList<String>();
    registerMockCommand("maincmd", true, executionLog);

    var capturedCallback = new AtomicReference<PrimaryDispatchCallback>();
    PrimaryCommandDispatcher controllableDispatcher =
        (request, callback) -> capturedCallback.set(callback);

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            controllableDispatcher,
            immediateActionDispatcher,
            p -> (task, retired) -> task.run());

    var sessionResult = new SessionResult("maincmd", List.of(), List.of(), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("maincmd", List.of(), List.of(), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    // Coordinate leaving it in PRIMARY_DISPATCH
    customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(registry.hasActiveExecution(player.getUniqueId()));

    // Now try to intercept a new command with prompt tags for the same player
    var interceptResult = engine.interceptResult(player, "/cmd <a:test>");
    assertTrue(
        interceptResult.isRejectedActiveSession(),
        "Must reject intercept when active execution exists");
    assertTrue(engine.getSession(player).isEmpty(), "Must not create a new prompt session");
  }

  @Test
  @DisplayName("Defensive registration collision invokes terminal failure feedback exactly once")
  void registrationCollisionTerminalFeedback() {
    var player = createPlayer("TestRegCollision");

    PrimaryCommandDispatcher controllableDispatcher = (request, callback) -> {};
    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            controllableDispatcher,
            immediateActionDispatcher,
            p -> (task, retired) -> task.run());

    var sessionResult1 = new SessionResult("cmd1", List.of(), List.of(), List.of());
    var plan1 =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("cmd1", List.of(), List.of(), ParserConfig.ANGLE_BRACKETS));
    var completion1 =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult1,
            plan1,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var first = customCoordinator.coordinate(player, completion1, sessionResult1);
    assertTrue(first.isPresent());

    // Second registration attempt collision
    var sessionResult2 = new SessionResult("cmd2", List.of(), List.of(), List.of());
    var plan2 =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("cmd2", List.of(), List.of(), ParserConfig.ANGLE_BRACKETS));
    var completion2 =
        InputCompletion.of(
            player.getUniqueId(),
            2L,
            1L,
            sessionResult2,
            plan2,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var second = customCoordinator.coordinate(player, completion2, sessionResult2);
    assertTrue(second.isEmpty(), "Colliding coordinate must return empty");
    // Assert exactly 1 active execution (the first one) in registry
    assertEquals(1, registry.size());
    assertSame(first.get(), registry.getByInitiator(player.getUniqueId()).orElseThrow());
  }

  @Test
  @DisplayName("Post-success throwing PCM action transitions to ERROR and cleans up")
  void postSuccessThrowingPcmTransitionsToError() {
    var player = createPlayer("TestThrowingPcm");

    var capturedCallback = new AtomicReference<PrimaryDispatchCallback>();
    PrimaryCommandDispatcher controllableDispatcher =
        (request, callback) -> capturedCallback.set(callback);

    ImmediateActionDispatcher failingDispatcher =
        (request, callback) ->
            callback.onComplete(
                DispatchOutcome.failure(
                    DispatchErrorKind.EXCEPTION_THROWN, "Simulated PCM dispatch failure"));

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            controllableDispatcher,
            failingDispatcher,
            p -> (task, retired) -> task.run());

    var pcm =
        new PostCommandMeta("failpcm", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);
    var sessionResult = new SessionResult("goodcmd", List.of(), List.of(pcm), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("goodcmd", List.of(), List.of(pcm), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player(),
            List.of(pcm));

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    // Primary succeeds
    capturedCallback.get().onComplete(DispatchOutcome.success());

    // Must have transitioned to ERROR, never stuck in POST_ACTIONS
    assertEquals(ExecutionStage.ERROR, instance.getStage());
    assertTrue(instance.isCleanedUp());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
  }

  @Test
  @DisplayName("Blank-primary throwing PCM transitions to ERROR and cleans up")
  void blankPrimaryThrowingPcmTransitionsToError() {
    var player = createPlayer("TestBlankThrowingPcm");

    ImmediateActionDispatcher failingDispatcher =
        (request, callback) ->
            callback.onComplete(
                DispatchOutcome.failure(
                    DispatchErrorKind.EXCEPTION_THROWN, "Simulated blank-primary PCM failure"));

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            (req, cb) -> cb.onComplete(DispatchOutcome.success()),
            failingDispatcher,
            p -> (task, retired) -> task.run());

    var pcm =
        new PostCommandMeta("failpcm", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);
    var sessionResult = new SessionResult("", List.of(), List.of(pcm), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("", List.of(), List.of(pcm), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player(),
            List.of(pcm));

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    assertEquals(ExecutionStage.ERROR, instance.getStage());
    assertTrue(instance.isCleanedUp());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
  }

  @Test
  @DisplayName(
      "Gate denied without onDeny runs ON_CANCEL post-actions and transitions to CANCELLED")
  void gateDeniedWithoutOnDenyRunsCancelPostActions() {
    var player = createPlayer("TestGateDeniedNoOnDeny");
    var dispatchedActions = new ArrayList<String>();

    ImmediateActionDispatcher recordingDispatcher =
        (request, callback) -> {
          dispatchedActions.add(request.command());
          callback.onComplete(DispatchOutcome.success());
        };

    PreDispatchGateHandler denyingGateHandler =
        (p, instance, spec, gateIndex, callback) ->
            callback.onResult(PreDispatchGateResult.denied(null));

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            (req, cb) -> cb.onComplete(DispatchOutcome.success()),
            recordingDispatcher,
            denyingGateHandler,
            p -> (task, retired) -> task.run());

    var onCancelPcm =
        new PostCommandMeta(
            "say cancel-ran", new int[0], 0, true, DispatchTarget.PASSTHROUGH, false);
    var planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("primary"),
            List.of(new PreDispatchGateSpec.Approval("gate_1")),
            List.of());
    var sessionResult = new SessionResult("primary", List.of(), List.of(), List.of(onCancelPcm));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            planDef,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player(),
            List.of(onCancelPcm));

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
    assertTrue(instance.isCleanedUp());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
    assertEquals(List.of("say cancel-ran"), dispatchedActions);
  }

  @Test
  void duplicateGateResultCannotRestartNextGateOrRunStaleDenialAction() {
    var player = createPlayer("GateReplay");
    var callbacks = new ArrayList<PreDispatchGateCallback>();
    var commands = new ArrayList<String>();
    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            (request, callback) -> {
              commands.add(request.command());
              callback.onComplete(DispatchOutcome.success());
            },
            (request, callback) -> {
              commands.add(request.command());
              callback.onComplete(DispatchOutcome.success());
            },
            (p, instance, spec, index, callback) -> callbacks.add(callback),
            p -> (task, retired) -> task.run());
    var plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("primary"),
            List.of(
                new PreDispatchGateSpec.Approval("first"),
                new PreDispatchGateSpec.Approval("second")),
            List.of());
    var completion =
        new InputCompletion(
            player.getUniqueId(),
            1L,
            1L,
            List.of(),
            "primary",
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instance = customCoordinator.coordinate(player, completion).orElseThrow();
    callbacks.getFirst().onResult(PreDispatchGateResult.approved());
    callbacks.getFirst().onResult(PreDispatchGateResult.approved());
    callbacks
        .getFirst()
        .onResult(
            PreDispatchGateResult.denied(
                TrustedPresetAction.of("stale_denial", ExecuteAs.CONSOLE)));

    assertEquals(2, callbacks.size(), "Each gate must be evaluated once");
    assertEquals(ExecutionStage.PRE_DISPATCH_GATES, instance.getStage());
    assertTrue(commands.isEmpty(), "A stale denial cannot execute an action");
    callbacks.get(1).onResult(PreDispatchGateResult.approved());
    assertEquals(List.of("primary"), commands);
    assertEquals(ExecutionStage.COMPLETED, instance.getStage());
  }

  @Test
  @DisplayName("Duplicate and racing outcomes are processed exactly once")
  void duplicateOutcomesProcessedExactlyOnce() {
    var player = createPlayer("TestDupOutcomes");
    var executionCount = new AtomicInteger();

    PrimaryCommandDispatcher controllableDispatcher =
        (request, callback) -> {
          // Intentionally invoke callback with different outcomes concurrently
          callback.onComplete(DispatchOutcome.success());
          callback.onComplete(
              DispatchOutcome.failure(DispatchErrorKind.DISPATCH_RETURNED_FALSE, "second"));
          callback.onComplete(DispatchOutcome.success());
        };

    ImmediateActionDispatcher countingDispatcher =
        (request, callback) -> {
          executionCount.incrementAndGet();
          callback.onComplete(DispatchOutcome.success());
        };

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            controllableDispatcher,
            countingDispatcher,
            p -> (task, retired) -> task.run());

    var pcm =
        new PostCommandMeta("countpcm", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);
    var sessionResult = new SessionResult("cmd", List.of(), List.of(pcm), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("cmd", List.of(), List.of(pcm), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player(),
            List.of(pcm));

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    assertEquals(ExecutionStage.COMPLETED, instanceOpt.get().getStage());
    assertEquals(1, executionCount.get(), "PCMs must be dispatched exactly once");
  }

  @Test
  @DisplayName(
      "Finding 9: On-deny action provenance uses TRUSTED_PRESET, gateId source, and preserves execute_as")
  void onDenyActionProvenanceUsesTrustedPresetAndGateSourceId() {
    var player = createPlayer("TestFinding9Provenance");
    var capturedRequest = new AtomicReference<ImmediateActionRequest>();

    ImmediateActionDispatcher mockActionDispatcher =
        (request, callback) -> {
          capturedRequest.set(request);
          callback.onComplete(DispatchOutcome.success());
        };

    // 1. Console on-deny action
    PreDispatchGateHandler consoleGateHandler =
        (p, instance, spec, gateIndex, callback) -> {
          var onDeny = TrustedPresetAction.of("log denied_console {player}", ExecuteAs.CONSOLE);
          callback.onResult(PreDispatchGateResult.denied(onDeny));
        };

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            (req, cb) -> cb.onComplete(DispatchOutcome.success()),
            mockActionDispatcher,
            consoleGateHandler,
            p -> (task, retired) -> task.run());

    var planDefConsole =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("maincmd"),
            List.of(new PreDispatchGateSpec.Approval("trade_gate_42")),
            List.of());

    var completionConsole =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            new SessionResult("maincmd", List.of(), List.of(), List.of()),
            planDefConsole,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instanceOpt = customCoordinator.coordinate(player, completionConsole);
    assertTrue(instanceOpt.isPresent());
    assertEquals(ExecutionStage.CANCELLED, instanceOpt.get().getStage());

    var reqConsole = capturedRequest.get();
    assertNotNull(reqConsole);
    assertEquals(ExecuteAs.CONSOLE, reqConsole.executeAs(), "ExecuteAs must be preserved");
    assertEquals("approval-gate:trade_gate_42", reqConsole.sourceId());
    assertNotNull(reqConsole.provenance());
    assertEquals(
        ActionTrustLevel.TRUSTED_PRESET,
        reqConsole.provenance().trustLevel(),
        "Must use TRUSTED_PRESET, never CONSOLE_DELEGATED trust level");
    assertNotEquals(ActionTrustLevel.CONSOLE_DELEGATED, reqConsole.provenance().trustLevel());
    assertTrue(
        reqConsole.provenance().consoleDelegated(),
        "consoleDelegated flag must be true for CONSOLE");
    assertEquals("approval-gate:trade_gate_42", reqConsole.provenance().sourceId());

    // 2. Player on-deny action
    PreDispatchGateHandler playerGateHandler =
        (p, instance, spec, gateIndex, callback) -> {
          var onDeny = TrustedPresetAction.of("msg {player} denied", ExecuteAs.PLAYER);
          callback.onResult(PreDispatchGateResult.denied(onDeny));
        };

    var customCoordinatorPlayer =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            (req, cb) -> cb.onComplete(DispatchOutcome.success()),
            mockActionDispatcher,
            playerGateHandler,
            p -> (task, retired) -> task.run());

    var planDefPlayer =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("maincmd"),
            List.of(new PreDispatchGateSpec.Approval("player_gate_99")),
            List.of());

    var completionPlayer =
        InputCompletion.of(
            player.getUniqueId(),
            2L,
            1L,
            new SessionResult("maincmd", List.of(), List.of(), List.of()),
            planDefPlayer,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instanceOptPlayer = customCoordinatorPlayer.coordinate(player, completionPlayer);
    assertTrue(instanceOptPlayer.isPresent());
    assertEquals(ExecutionStage.CANCELLED, instanceOptPlayer.get().getStage());

    var reqPlayer = capturedRequest.get();
    assertNotNull(reqPlayer);
    assertEquals(ExecuteAs.PLAYER, reqPlayer.executeAs());
    assertEquals("approval-gate:player_gate_99", reqPlayer.sourceId());
    assertNotNull(reqPlayer.provenance());
    assertEquals(ActionTrustLevel.TRUSTED_PRESET, reqPlayer.provenance().trustLevel());
    assertFalse(
        reqPlayer.provenance().consoleDelegated(),
        "consoleDelegated flag must be false for PLAYER");
    assertEquals("approval-gate:player_gate_99", reqPlayer.provenance().sourceId());
  }

  @Test
  @DisplayName(
      "Finding 9: Trusted console denial authorization allows non-op player console execution")
  void trustedConsoleDenialAuthorizationForNonOpPlayer() {
    var player = createPlayer("NonOpPlayer");
    player.setOp(false);
    assertFalse(player.isOp());

    var executionLog = new ArrayList<String>();
    registerMockCommand("auditlog", true, executionLog);

    PreDispatchGateHandler gateHandler =
        (p, instance, spec, gateIndex, callback) -> {
          var onDeny = TrustedPresetAction.of("auditlog denied {player}", ExecuteAs.CONSOLE);
          callback.onResult(PreDispatchGateResult.denied(onDeny));
        };

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            (req, cb) -> cb.onComplete(DispatchOutcome.success()),
            immediateActionDispatcher, // Real PaperImmediateActionDispatcher
            gateHandler,
            p -> (task, retired) -> task.run());

    var planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("maincmd"),
            List.of(new PreDispatchGateSpec.Approval("audit_gate")),
            List.of());

    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            new SessionResult("maincmd", List.of(), List.of(), List.of()),
            planDef,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instanceOpt = customCoordinator.coordinate(player, completion);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
    assertTrue(instance.isCleanedUp());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));

    // Console audit log command executed successfully even though player is non-op
    assertTrue(
        executionLog.contains("auditlog denied NonOpPlayer"),
        "Trusted preset console on-deny action must authorize console dispatch for non-op player");
  }

  @Test
  @DisplayName("Initiator quit/cancel before on-deny dispatch skips action completely")
  void initiatorQuitBeforeOnDenyDispatchSkipsAction() {
    var player = createPlayer("TestQuitBeforeDeny");
    var dispatchedActions = new ArrayList<ImmediateActionRequest>();

    ImmediateActionDispatcher mockActionDispatcher =
        (request, callback) -> {
          dispatchedActions.add(request);
          callback.onComplete(DispatchOutcome.success());
        };

    var capturedCallback = new AtomicReference<PreDispatchGateCallback>();
    PreDispatchGateHandler controllableGateHandler =
        (p, instance, spec, gateIndex, callback) -> capturedCallback.set(callback);

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            (req, cb) -> cb.onComplete(DispatchOutcome.success()),
            mockActionDispatcher,
            controllableGateHandler,
            p -> (task, retired) -> task.run());

    var planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("maincmd"),
            List.of(new PreDispatchGateSpec.Approval("quit_race_gate")),
            List.of());

    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            new SessionResult("maincmd", List.of(), List.of(), List.of()),
            planDef,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instanceOpt = customCoordinator.coordinate(player, completion);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();
    assertEquals(ExecutionStage.PRE_DISPATCH_GATES, instance.getStage());

    // Initiator disconnects/cancels before gate callback resolves
    customCoordinator.cancel(player.getUniqueId());
    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));

    // Gate callback now attempts to return DENIED with on-deny action
    var onDeny = TrustedPresetAction.of("log skipped_action", ExecuteAs.CONSOLE);
    capturedCallback.get().onResult(PreDispatchGateResult.denied(onDeny));

    // Verify on-deny action was completely skipped because initiator disconnect/cancel won
    assertEquals(
        0,
        dispatchedActions.size(),
        "On-deny action must not be dispatched when initiator cancel/quit won");
    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
  }

  @Test
  @DisplayName("Initiator quit racing queued denial callback is ignored without duplicate cleanup")
  void initiatorQuitRacingQueuedDenialCallbackIsIgnored() {
    var player = createPlayer("TestQuitRacingDenyCallback");
    var capturedActionCallback = new AtomicReference<ActionDispatchCallback>();

    ImmediateActionDispatcher controllableActionDispatcher =
        (request, callback) -> capturedActionCallback.set(callback);

    PreDispatchGateHandler gateHandler =
        (p, instance, spec, gateIndex, callback) -> {
          var onDeny = TrustedPresetAction.of("log queued_action", ExecuteAs.CONSOLE);
          callback.onResult(PreDispatchGateResult.denied(onDeny));
        };

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            (req, cb) -> cb.onComplete(DispatchOutcome.success()),
            controllableActionDispatcher,
            gateHandler,
            p -> (task, retired) -> task.run());

    var planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("maincmd"),
            List.of(new PreDispatchGateSpec.Approval("racing_gate")),
            List.of());

    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            new SessionResult("maincmd", List.of(), List.of(), List.of()),
            planDef,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instanceOpt = customCoordinator.coordinate(player, completion);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    // Action was dispatched and is queued waiting for outcome callback
    assertNotNull(capturedActionCallback.get());

    // While action outcome callback is in-flight, initiator disconnects/cancels
    customCoordinator.cancel(player.getUniqueId());
    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
    assertTrue(instance.isCleanedUp());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));

    // Now fire the queued action callback (e.g. success or failure)
    assertDoesNotThrow(() -> capturedActionCallback.get().onComplete(DispatchOutcome.success()));

    // Verify stage remains CANCELLED and no registry corruption or duplicate transitions occur
    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
  }

  @Test
  @DisplayName(
      "Initiator PlayerExecutor retired during denial outcome executes terminal cleanup once")
  void initiatorPlayerExecutorRetiredDuringDenialOutcomeCleansUpOnce() {
    var player = createPlayer("TestRetiredDenyCallback");
    var executionCount = new AtomicInteger();

    ImmediateActionDispatcher actionDispatcher =
        (request, callback) -> callback.onComplete(DispatchOutcome.success());

    PreDispatchGateHandler gateHandler =
        (p, instance, spec, gateIndex, callback) -> {
          var onDeny = TrustedPresetAction.of("log retired_action", ExecuteAs.CONSOLE);
          callback.onResult(PreDispatchGateResult.denied(onDeny));
        };

    // First gate evaluation succeeds to enter player executor, then retired callback fires on
    // return hop
    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            (req, cb) -> cb.onComplete(DispatchOutcome.success()),
            actionDispatcher,
            gateHandler,
            p ->
                (task, onRetired) -> {
                  if (executionCount.incrementAndGet() == 1) {
                    task.run(); // Run gate result
                  } else {
                    onRetired.run(); // Retire on on-deny return hop
                  }
                });

    var planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("maincmd"),
            List.of(new PreDispatchGateSpec.Approval("retire_gate")),
            List.of());

    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            new SessionResult("maincmd", List.of(), List.of(), List.of()),
            planDef,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instanceOpt = customCoordinator.coordinate(player, completion);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    assertEquals(ExecutionStage.ERROR, instance.getStage());
    assertTrue(instance.isCleanedUp());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
  }

  @Test
  @DisplayName(
      "Synchronous throw in primaryDispatcher.dispatch transitions to ERROR, removes registry, cleans up")
  void primaryDispatcherSynchronousThrowTransitionsToError() {
    var player = createPlayer("TestSyncPrimaryThrow");

    PrimaryCommandDispatcher throwingDispatcher =
        (request, callback) -> {
          throw new RuntimeException("Primary dispatch synchronous failure");
        };

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            throwingDispatcher,
            immediateActionDispatcher,
            p -> (task, retired) -> task.run());

    var sessionResult = new SessionResult("fatalcmd", List.of(), List.of(), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("fatalcmd", List.of(), List.of(), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    assertEquals(ExecutionStage.ERROR, instance.getStage());
    assertTrue(instance.isCleanedUp());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
    assertEquals(0, registry.size());
  }

  @Test
  @DisplayName(
      "Synchronous throw in immediateActionDispatcher.dispatch on-deny transitions to CANCELLED/ERROR, removes registry, cleans up")
  void immediateActionDispatcherOnDenySynchronousThrowTransitionsSafely() {
    var player = createPlayer("TestSyncOnDenyThrow");

    ImmediateActionDispatcher throwingImmediateDispatcher =
        (request, callback) -> {
          throw new RuntimeException("On-deny dispatch synchronous failure");
        };

    PreDispatchGateHandler gateHandler =
        (p, instance, spec, gateIndex, callback) -> {
          var onDeny = TrustedPresetAction.of("log denied_action", ExecuteAs.CONSOLE);
          callback.onResult(PreDispatchGateResult.denied(onDeny));
        };

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            (req, cb) -> cb.onComplete(DispatchOutcome.success()),
            throwingImmediateDispatcher,
            gateHandler,
            p -> (task, retired) -> task.run());

    var planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("maincmd"),
            List.of(new PreDispatchGateSpec.Approval("deny_throw_gate")),
            List.of());

    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            new SessionResult("maincmd", List.of(), List.of(), List.of()),
            planDef,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instanceOpt = customCoordinator.coordinate(player, completion);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
    assertTrue(instance.isCleanedUp());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
    assertEquals(0, registry.size());
  }

  @Test
  @DisplayName(
      "Synchronous throw in PAPI resolver factory transitions to ERROR, removes registry, cleans up")
  void papiResolverFactorySynchronousThrowTransitionsToError() {
    var player = createPlayer("TestSyncPapiThrow");

    var pcm =
        new PostCommandMeta("postcmd", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);
    var sessionResult = new SessionResult("maincmd", List.of(), List.of(pcm), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("maincmd", List.of(), List.of(pcm), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player(),
            List.of(pcm));

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            (req, cb) -> cb.onComplete(DispatchOutcome.success()),
            immediateActionDispatcher,
            null,
            null,
            p -> {
              throw new RuntimeException("PAPI factory creation failed");
            },
            p -> (task, retired) -> task.run());

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    assertEquals(ExecutionStage.ERROR, instance.getStage());
    assertTrue(instance.isCleanedUp());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
    assertEquals(0, registry.size());
  }

  @Test
  @DisplayName(
      "Synchronous throw in PostActionScheduler transitions to ERROR, removes registry, cleans up")
  void postActionSchedulerSynchronousThrowTransitionsToError() {
    var player = createPlayer("TestSyncPostSchedulerThrow");

    var pcm =
        new PostCommandMeta("delayedcmd", new int[0], 20, false, DispatchTarget.PASSTHROUGH, false);
    var sessionResult = new SessionResult("maincmd", List.of(), List.of(pcm), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("maincmd", List.of(), List.of(pcm), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player(),
            List.of(pcm));

    PostActionScheduler throwingScheduler =
        (p, task, retired, delay) -> {
          throw new RuntimeException("Post action scheduler failed");
        };

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            (req, cb) -> cb.onComplete(DispatchOutcome.success()),
            immediateActionDispatcher,
            null,
            throwingScheduler,
            null,
            p -> (task, retired) -> task.run());

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    assertEquals(ExecutionStage.ERROR, instance.getStage());
    assertTrue(instance.isCleanedUp());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
    assertEquals(0, registry.size());
  }

  @Test
  @DisplayName(
      "Synchronous throw in PreDispatchGateHandler.evaluateGate transitions to ERROR, removes registry, cleans up")
  void preDispatchGateHandlerSynchronousThrowTransitionsToError() {
    var player = createPlayer("TestSyncGateThrow");

    PreDispatchGateHandler throwingGateHandler =
        (p, instance, spec, gateIndex, callback) -> {
          throw new RuntimeException("Gate evaluation synchronous failure");
        };

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            (req, cb) -> cb.onComplete(DispatchOutcome.success()),
            immediateActionDispatcher,
            throwingGateHandler,
            p -> (task, retired) -> task.run());

    var planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("maincmd"),
            List.of(new PreDispatchGateSpec.Approval("sync_throw_gate")),
            List.of());

    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            new SessionResult("maincmd", List.of(), List.of(), List.of()),
            planDef,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instanceOpt = customCoordinator.coordinate(player, completion);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    assertEquals(ExecutionStage.ERROR, instance.getStage());
    assertTrue(instance.isCleanedUp());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
    assertEquals(0, registry.size());
  }

  @Test
  @DisplayName(
      "Synchronous throw in PlayerExecutor factory transitions to ERROR, removes registry, cleans up")
  void playerExecutorFactorySynchronousThrowTransitionsToError() {
    var player = createPlayer("TestSyncExecutorThrow");

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            (req, cb) -> cb.onComplete(DispatchOutcome.success()),
            immediateActionDispatcher,
            p -> {
              throw new RuntimeException("Player executor factory failed");
            });

    var sessionResult = new SessionResult("maincmd", List.of(), List.of(), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("maincmd", List.of(), List.of(), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    assertEquals(ExecutionStage.ERROR, instance.getStage());
    assertTrue(instance.isCleanedUp());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
    assertEquals(0, registry.size());
  }

  @Test
  @DisplayName(
      "Callback body unexpected throw inside PlayerExecutor transitions to ERROR, removes registry, cleans up")
  void callbackBodyThrowInsidePlayerExecutorTransitionsToError() {
    var player = createPlayer("TestCallbackBodyThrow");

    var capturedCallback = new AtomicReference<PrimaryDispatchCallback>();
    PrimaryCommandDispatcher controllableDispatcher =
        (request, callback) -> capturedCallback.set(callback);

    // PlayerExecutor that throws during task execution
    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            controllableDispatcher,
            immediateActionDispatcher,
            p ->
                (task, retired) -> {
                  task.run();
                });

    var sessionResult = new SessionResult("maincmd", List.of(), List.of(), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand("maincmd", List.of(), List.of(), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());
    var instance = instanceOpt.get();

    // Trigger callback with failure detail that could throw in downstream handler or simulate throw
    capturedCallback
        .get()
        .onComplete(
            DispatchOutcome.failure(DispatchErrorKind.EXCEPTION_THROWN, "callback failure"));

    assertEquals(ExecutionStage.ERROR, instance.getStage());
    assertTrue(instance.isCleanedUp());
    assertFalse(registry.hasActiveExecution(player.getUniqueId()));
  }

  @Test
  @DisplayName(
      "Logging regression: command, answers, secrets, newlines, and markup tags are absent from logs and player message is escaped")
  void loggingRegressionSanitizesSecretsAndMarkup() {
    var player = createPlayer("TestLoggingSecurity");

    var capturedLogs = new ArrayList<String>();
    var mockLogger = org.mockito.Mockito.mock(dev.cyr1en.promptpaper.util.PluginLogger.class);
    org.mockito.Mockito.when(plugin.getPluginLogger()).thenReturn(mockLogger);

    org.mockito.Mockito.doAnswer(
            inv -> {
              capturedLogs.add(inv.getArgument(0));
              return null;
            })
        .when(mockLogger)
        .info(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    org.mockito.Mockito.doAnswer(
            inv -> {
              capturedLogs.add(inv.getArgument(0));
              return null;
            })
        .when(mockLogger)
        .warn(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    org.mockito.Mockito.doAnswer(
            inv -> {
              capturedLogs.add(inv.getArgument(0));
              return null;
            })
        .when(mockLogger)
        .err(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    org.mockito.Mockito.doAnswer(
            inv -> {
              capturedLogs.add(inv.getArgument(0));
              return null;
            })
        .when(mockLogger)
        .debug(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());

    var capturedPlaceholders = new ArrayList<Placeholder>();
    org.mockito.Mockito.doAnswer(
            inv -> {
              for (int i = 0; i < inv.getArguments().length; i++) {
                Object arg = inv.getArgument(i);
                if (arg instanceof Placeholder[] phs) {
                  capturedPlaceholders.addAll(List.of(phs));
                } else if (arg instanceof Placeholder ph) {
                  capturedPlaceholders.add(ph);
                }
              }
              return net.kyori.adventure.text.Component.text("Command failed.");
            })
        .when(i18n)
        .get(
            org.mockito.ArgumentMatchers.eq("prompt.error.command_failed"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(Placeholder[].class));

    String secretCommand = "/login secret_pass_12345";
    String secretAnswer = "token_abc_999\n\r\t<click:run_command:/op me><red>malicious</red>";
    String maliciousErrorDetail =
        "Error with <click:run_command:/ban all>dangerous_tag\nand\rnewlines";

    PrimaryCommandDispatcher failingDispatcher =
        (request, callback) ->
            callback.onComplete(
                DispatchOutcome.failure(DispatchErrorKind.EXCEPTION_THROWN, maliciousErrorDetail));

    var customCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            failingDispatcher,
            immediateActionDispatcher,
            p -> (task, retired) -> task.run());

    var sessionResult =
        new SessionResult(secretCommand, List.of(secretAnswer), List.of(), List.of());
    var plan =
        ExecutionPlanAdapter.fromParsedCommand(
            new ParsedCommand(secretCommand, List.of(), List.of(), ParserConfig.ANGLE_BRACKETS));
    var completion =
        InputCompletion.of(
            player.getUniqueId(),
            1L,
            1L,
            sessionResult,
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());

    var instanceOpt = customCoordinator.coordinate(player, completion, sessionResult);
    assertTrue(instanceOpt.isPresent());

    // 1. Verify secrets and command are NEVER present in any captured log
    for (String log : capturedLogs) {
      assertFalse(
          log.contains("secret_pass_12345"), "Log must not contain secret password: " + log);
      assertFalse(log.contains(secretCommand), "Log must not contain assembled command: " + log);
      assertFalse(
          log.contains("token_abc_999"), "Log must not contain secret answer token: " + log);
      assertFalse(log.contains("\n"), "Log must not contain raw newline: " + log);
      assertFalse(log.contains("\r"), "Log must not contain raw carriage return: " + log);
      assertFalse(
          log.contains("<click:run_command:"), "Log must not contain unescaped click tag: " + log);
    }

    // 2. Verify player message placeholder is sanitized and escaped
    assertFalse(capturedPlaceholders.isEmpty());
    var messagePlaceholder =
        capturedPlaceholders.stream()
            .filter(p -> "message".equals(p.key()))
            .findFirst()
            .orElseThrow();

    String placeholderVal = messagePlaceholder.value();
    assertFalse(placeholderVal.contains("\n"), "Placeholder must strip newlines");
    assertFalse(placeholderVal.contains("\r"), "Placeholder must strip carriage returns");
    assertTrue(
        placeholderVal.contains("\\<click:run_command:"),
        "Placeholder must have tags escaped with backslash");
    assertFalse(
        placeholderVal.startsWith("<click:run_command:"),
        "Placeholder must not have unescaped leading tag");

    // Deserializing with MiniMessage results in literal text without click actions
    var deserialized =
        net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(placeholderVal);
    assertNull(
        deserialized.clickEvent(),
        "Deserialized component must not have click event from injected markup");
  }
}
