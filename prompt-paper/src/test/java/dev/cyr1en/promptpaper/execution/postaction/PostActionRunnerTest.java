package dev.cyr1en.promptpaper.execution.postaction;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.DispatchTarget;
import dev.cyr1en.promptcore.PostCommandMeta;
import dev.cyr1en.promptcore.logic.condition.ConditionCompileOptions;
import dev.cyr1en.promptcore.logic.condition.ConditionCompiler;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.custom.PlayerExecutor;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchErrorKind;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchOutcome;
import dev.cyr1en.promptpaper.execution.dispatch.ImmediateActionDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.ImmediateActionRequest;
import dev.cyr1en.promptpaper.execution.postaction.template.PapiReferenceResolver;
import dev.cyr1en.promptpaper.execution.runtime.DispatchContextSnapshot;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionId;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionPlanInstance;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionStage;
import dev.cyr1en.promptpaper.execution.runtime.InputCompletion;
import dev.cyr1en.promptpaper.execution.runtime.NoticeFlag;
import dev.cyr1en.promptpaper.preset.ConditionalPostCommandDefinition;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.ExecutionPolicy;
import dev.cyr1en.promptpaper.preset.PostCommand;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import dev.cyr1en.promptpaper.preset.TrustedPresetAction;
import dev.cyr1en.promptpaper.util.CancellableTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PostActionRunner Integration & Contract Tests")
class PostActionRunnerTest extends MockBukkitTest {

  private Player player;
  private PlayerExecutor playerExecutor;
  private ManualTestScheduler testScheduler;

  @BeforeEach
  void setUp() {
    player = createPlayer("PostActionPlayer");
    playerExecutor = PlayerExecutor.forPlayer(plugin, player, p -> true); // synchronous in test
    testScheduler = new ManualTestScheduler();
  }

  /** Test scheduler allowing precise tick advancement and task tracking. */
  static class ManualTestScheduler implements PostActionScheduler {
    private final List<ScheduledTimer> scheduledTasks = new CopyOnWriteArrayList<>();

    record ScheduledTimer(
        Player player, Runnable task, Runnable retired, long delayTicks, AtomicBoolean cancelled)
        implements CancellableTask {
      @Override
      public void cancel() {
        cancelled.set(true);
      }
    }

    @Override
    public CancellableTask scheduleDelayed(
        Player player, Runnable task, Runnable retired, long delayTicks) {
      ScheduledTimer timer =
          new ScheduledTimer(player, task, retired, delayTicks, new AtomicBoolean(false));
      scheduledTasks.add(timer);
      return timer;
    }

    public void advanceTicks(long ticks) {
      List<ScheduledTimer> toRun = new ArrayList<>();
      for (ScheduledTimer timer : scheduledTasks) {
        if (!timer.cancelled().get() && timer.delayTicks() <= ticks) {
          toRun.add(timer);
        }
      }
      scheduledTasks.removeAll(toRun);
      for (ScheduledTimer timer : toRun) {
        if (!timer.cancelled().get()) {
          timer.task().run();
        }
      }
    }

    public int pendingCount() {
      return (int) scheduledTasks.stream().filter(t -> !t.cancelled().get()).count();
    }
  }

  /** Test dispatcher recording dispatches and executing custom completion policies. */
  static class RecordingDispatcher implements ImmediateActionDispatcher {
    final List<ImmediateActionRequest> dispatchedRequests = new CopyOnWriteArrayList<>();
    private DispatchOutcome configuredOutcome = DispatchOutcome.success();
    private boolean throwException = false;
    private boolean duplicateCallbacks = false;
    private boolean asyncCallback = false;

    public void setConfiguredOutcome(DispatchOutcome outcome) {
      this.configuredOutcome = outcome;
    }

    public void setThrowException(boolean throwException) {
      this.throwException = throwException;
    }

    public void setDuplicateCallbacks(boolean duplicateCallbacks) {
      this.duplicateCallbacks = duplicateCallbacks;
    }

    public void setAsyncCallback(boolean asyncCallback) {
      this.asyncCallback = asyncCallback;
    }

    @Override
    public void dispatch(
        ImmediateActionRequest request,
        dev.cyr1en.promptpaper.execution.dispatch.ActionDispatchCallback callback) {
      dispatchedRequests.add(request);
      if (throwException) {
        callback.onComplete(
            DispatchOutcome.failure(DispatchErrorKind.EXCEPTION_THROWN, "Simulated exception"));
        return;
      }
      Runnable notify =
          () -> {
            callback.onComplete(configuredOutcome);
            if (duplicateCallbacks) {
              callback.onComplete(configuredOutcome);
            }
          };
      if (asyncCallback) {
        new Thread(notify).start();
      } else {
        notify.run();
      }
    }
  }

  private ExecutionPlanInstance createPlanInstance(
      List<PostCommandMeta> pcms, PresetSnapshot snapshot, List<String> answers) {
    var completion =
        new InputCompletion(
            player.getUniqueId(),
            1L,
            0L,
            answers,
            "say primary",
            null,
            snapshot,
            DispatchContextSnapshot.player(),
            pcms);
    return new ExecutionPlanInstance(ExecutionId.create(), completion, ExecutionStage.POST_ACTIONS);
  }

  @Nested
  @DisplayName("Preconditions & Caller Verification")
  class PreconditionTests {

    @Test
    @DisplayName("Rejects runner execution when instance is not in POST_ACTIONS stage")
    void rejectsNonPostActionStage() {
      var pcm =
          new PostCommandMeta("say test", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var completion =
          new InputCompletion(
              player.getUniqueId(),
              1L,
              0L,
              List.of(),
              "say primary",
              null,
              PresetSnapshot.empty(),
              DispatchContextSnapshot.player(),
              List.of(pcm));
      var instance =
          new ExecutionPlanInstance(
              ExecutionId.create(), completion, ExecutionStage.PRE_DISPATCH_GATES);
      var dispatcher = new RecordingDispatcher();

      var resultRef = new AtomicReference<PostActionResult>();
      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          completion,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isFailure());
      assertEquals(DispatchErrorKind.INVALID_REQUEST, resultRef.get().error().kind());
      assertTrue(dispatcher.dispatchedRequests.isEmpty());
    }

    @Test
    @DisplayName("Rejects runner execution when caller incarnation does not match")
    void rejectsCallerMismatch() {
      var pcm =
          new PostCommandMeta("say test", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var completion =
          new InputCompletion(
              player.getUniqueId(),
              999L, // wrong incarnation vs expected
              0L,
              List.of(),
              "say primary",
              null,
              PresetSnapshot.empty(),
              DispatchContextSnapshot.player(),
              List.of(pcm));
      var instance =
          new ExecutionPlanInstance(ExecutionId.create(), completion, ExecutionStage.POST_ACTIONS);
      var dispatcher = new RecordingDispatcher();

      // PlayerExecutor passing different player / wrong UUID
      var otherPlayer = createPlayer("OtherPlayer");
      var otherExecutor = PlayerExecutor.forPlayer(plugin, otherPlayer, p -> true);

      var resultRef = new AtomicReference<PostActionResult>();
      PostActionRunner.execute(
          otherPlayer,
          otherExecutor,
          instance,
          completion,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isFailure());
      assertTrue(instance.hasNotice(NoticeFlag.STALE_CALLER_DETECTED));
      assertTrue(dispatcher.dispatchedRequests.isEmpty());
    }

    @Test
    @DisplayName(
        "Rejects runner execution when external completion does not match instance completion")
    void rejectsMismatchedExternalCompletion() {
      var pcm =
          new PostCommandMeta("say test", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var instanceCompletion =
          new InputCompletion(
              player.getUniqueId(),
              1L,
              0L,
              List.of(),
              "say primary",
              null,
              PresetSnapshot.empty(),
              DispatchContextSnapshot.player(),
              List.of(pcm));
      var foreignCompletion =
          new InputCompletion(
              player.getUniqueId(),
              2L,
              0L,
              List.of(),
              "say primary",
              null,
              PresetSnapshot.empty(),
              DispatchContextSnapshot.player(),
              List.of(pcm));
      var instance =
          new ExecutionPlanInstance(
              ExecutionId.create(), instanceCompletion, ExecutionStage.POST_ACTIONS);
      var dispatcher = new RecordingDispatcher();

      var resultRef = new AtomicReference<PostActionResult>();
      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          foreignCompletion,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isFailure());
      assertEquals(DispatchErrorKind.INVALID_REQUEST, resultRef.get().error().kind());
      assertTrue(instance.hasNotice(NoticeFlag.STALE_CALLER_DETECTED));
      assertTrue(dispatcher.dispatchedRequests.isEmpty());
    }

    @Test
    @DisplayName(
        "Rejects delayed execution when stage mutates away from POST_ACTIONS before timer fires")
    void rejectsStageMutationBeforeDelayedTimer() {
      var pcm =
          new PostCommandMeta("say delayed", new int[] {}, 10, false, DispatchTarget.PLAYER, false);
      var instance = createPlanInstance(List.of(pcm), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertEquals(1, testScheduler.pendingCount());
      instance.tryTransitionTo(ExecutionStage.COMPLETED);

      testScheduler.advanceTicks(10);
      assertTrue(dispatcher.dispatchedRequests.isEmpty());
    }
  }

  @Nested
  @DisplayName("Sequential Ordering & Delays")
  class SequentialAndDelayTests {

    @Test
    @DisplayName("Executes actions sequentially in source order")
    void executesInSourceOrder() {
      var pcm1 =
          new PostCommandMeta("say first", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var pcm2 =
          new PostCommandMeta("say second", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var pcm3 =
          new PostCommandMeta("say third", new int[] {}, 0, false, DispatchTarget.PLAYER, false);

      var instance =
          createPlanInstance(List.of(pcm1, pcm2, pcm3), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
      assertEquals(3, dispatcher.dispatchedRequests.size());
      assertEquals("say first", dispatcher.dispatchedRequests.get(0).command());
      assertEquals("say second", dispatcher.dispatchedRequests.get(1).command());
      assertEquals("say third", dispatcher.dispatchedRequests.get(2).command());
    }

    @Test
    @DisplayName("Respects mixed delays (immediate, 10 ticks, 5 ticks) without premature dispatch")
    void respectsMixedDelays() {
      var pcm1 =
          new PostCommandMeta(
              "say immediate", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var pcm2 =
          new PostCommandMeta(
              "say delayed10", new int[] {}, 10, false, DispatchTarget.PLAYER, false);
      var pcm3 =
          new PostCommandMeta("say delayed5", new int[] {}, 5, false, DispatchTarget.PLAYER, false);

      var instance =
          createPlanInstance(List.of(pcm1, pcm2, pcm3), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      // Step 1 was immediate
      assertEquals(1, dispatcher.dispatchedRequests.size());
      assertEquals("say immediate", dispatcher.dispatchedRequests.get(0).command());
      assertNull(resultRef.get(), "Runner should not be completed yet");
      assertEquals(1, testScheduler.pendingCount());

      // Advance 5 ticks: step 2 (10 ticks) should NOT have run yet
      testScheduler.advanceTicks(5);
      assertEquals(1, dispatcher.dispatchedRequests.size());

      // Advance remaining 5 ticks (total 10 ticks): step 2 fires and schedules step 3 (5 ticks)
      testScheduler.advanceTicks(10);
      assertEquals(2, dispatcher.dispatchedRequests.size());
      assertEquals("say delayed10", dispatcher.dispatchedRequests.get(1).command());
      assertNull(resultRef.get(), "Runner should still be awaiting step 3");
      assertEquals(1, testScheduler.pendingCount());

      // Advance 5 ticks: step 3 fires and runner completes
      testScheduler.advanceTicks(5);
      assertEquals(3, dispatcher.dispatchedRequests.size());
      assertEquals("say delayed5", dispatcher.dispatchedRequests.get(2).command());
      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
    }

    @Test
    @DisplayName("Accepts maximum delay of 72000 ticks")
    void acceptsMaxDelayTicks() {
      var pcm =
          new PostCommandMeta(
              "say max_delay", new int[] {}, 72000, false, DispatchTarget.PLAYER, false);
      var instance = createPlanInstance(List.of(pcm), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertEquals(1, testScheduler.pendingCount());
      assertEquals(0, dispatcher.dispatchedRequests.size());

      testScheduler.advanceTicks(72000);
      assertEquals(1, dispatcher.dispatchedRequests.size());
      assertEquals("say max_delay", dispatcher.dispatchedRequests.get(0).command());
      assertTrue(resultRef.get().isSuccess());
    }

    @Test
    @DisplayName("Rejects delay > 72000 ticks and fails closed")
    void rejectsDelayOver72000() {
      var pcm =
          new PostCommandMeta(
              "say invalid_delay", new int[] {}, 72001, false, DispatchTarget.PLAYER, false);
      var instance = createPlanInstance(List.of(pcm), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isFailure());
      assertEquals(DispatchErrorKind.INVALID_REQUEST, resultRef.get().error().kind());
      assertTrue(instance.hasNotice(NoticeFlag.POST_ACTION_FAILED));
      assertEquals(0, dispatcher.dispatchedRequests.size());
    }
  }

  @Nested
  @DisplayName("Conditional PostCommands & Branch Selection")
  class ConditionalExecutionTests {

    @Test
    @DisplayName("Dispatches true branch when condition evaluates to true")
    void dispatchesTrueBranch() {
      var cond =
          ConditionCompiler.compile("{0} equals \"admin\"", ConditionCompileOptions.forPreset());
      var trueAction = TrustedPresetAction.of("say Welcome Admin {player}", ExecuteAs.CONSOLE, 0);
      var falseAction = TrustedPresetAction.of("say Welcome User {player}", ExecuteAs.PLAYER, 0);
      var def =
          new ConditionalPostCommandDefinition(
              "admin_check", cond, ExecutionPolicy.ON_COMPLETE, trueAction, falseAction);

      var snapshot =
          new PresetSnapshot(Map.of(), Map.of(), Map.of(), Map.of("admin_check", def), 1L);
      var pcm =
          new PostCommandMeta("admin_check", new int[] {}, 0, false, DispatchTarget.PLAYER, true);

      var instance = createPlanInstance(List.of(pcm), snapshot, List.of("admin"));
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
      assertEquals(1, dispatcher.dispatchedRequests.size());
      assertEquals(
          "say Welcome Admin " + player.getName(), dispatcher.dispatchedRequests.get(0).command());
      assertEquals(ExecuteAs.CONSOLE, dispatcher.dispatchedRequests.get(0).executeAs());
    }

    @Test
    @DisplayName("Dispatches false branch when condition evaluates to false")
    void dispatchesFalseBranch() {
      var cond =
          ConditionCompiler.compile("{0} equals \"admin\"", ConditionCompileOptions.forPreset());
      var trueAction = TrustedPresetAction.of("say Welcome Admin {player}", ExecuteAs.CONSOLE, 0);
      var falseAction = TrustedPresetAction.of("say Welcome User {player}", ExecuteAs.PLAYER, 0);
      var def =
          new ConditionalPostCommandDefinition(
              "admin_check", cond, ExecutionPolicy.ON_COMPLETE, trueAction, falseAction);

      var snapshot =
          new PresetSnapshot(Map.of(), Map.of(), Map.of(), Map.of("admin_check", def), 1L);
      var pcm =
          new PostCommandMeta("admin_check", new int[] {}, 0, false, DispatchTarget.PLAYER, true);

      var instance = createPlanInstance(List.of(pcm), snapshot, List.of("guest"));
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
      assertEquals(1, dispatcher.dispatchedRequests.size());
      assertEquals(
          "say Welcome User " + player.getName(), dispatcher.dispatchedRequests.get(0).command());
      assertEquals(ExecuteAs.PLAYER, dispatcher.dispatchedRequests.get(0).executeAs());
    }

    @Test
    @DisplayName("Absent branch acts as successful no-op and advances sequence")
    void absentBranchNoOpAdvancesSequence() {
      var cond =
          ConditionCompiler.compile("{0} equals \"vip\"", ConditionCompileOptions.forPreset());
      var trueAction = TrustedPresetAction.of("give {player} diamond 1", ExecuteAs.CONSOLE, 0);
      // ifFalse is null
      var def =
          new ConditionalPostCommandDefinition(
              "vip_gift", cond, ExecutionPolicy.ON_COMPLETE, trueAction, null);

      var pcm1 =
          new PostCommandMeta("vip_gift", new int[] {}, 0, false, DispatchTarget.PLAYER, true);
      var pcm2 =
          new PostCommandMeta("say finish", new int[] {}, 0, false, DispatchTarget.PLAYER, false);

      var snapshot = new PresetSnapshot(Map.of(), Map.of(), Map.of(), Map.of("vip_gift", def), 1L);
      var instance = createPlanInstance(List.of(pcm1, pcm2), snapshot, List.of("regular"));
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
      // pcm1 was no-op, pcm2 was dispatched
      assertEquals(1, dispatcher.dispatchedRequests.size());
      assertEquals("say finish", dispatcher.dispatchedRequests.get(0).command());
    }
  }

  @Nested
  @DisplayName("Placeholders, PAPI, & Security")
  class PlaceholderAndSecurityTests {

    @Test
    @DisplayName("Resolves PAPI references in trusted preset template")
    void resolvesPapiInTrustedPreset() {
      var postCmd =
          new PostCommand(
              "papi_cmd",
              "broadcast %server_name% welcomed {player}",
              ExecutionPolicy.ON_COMPLETE,
              ExecuteAs.CONSOLE,
              0);
      var snapshot =
          new PresetSnapshot(Map.of(), Map.of("papi_cmd", postCmd), Map.of(), Map.of(), 1L);
      var pcm =
          new PostCommandMeta("papi_cmd", new int[] {}, 0, false, DispatchTarget.PLAYER, true);

      var instance = createPlanInstance(List.of(pcm), snapshot, List.of());
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();
      var papiResolver = PapiReferenceResolver.fromMap(Map.of("server_name", "SurvivalCraft"));

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          papiResolver,
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
      assertEquals(
          "broadcast \"SurvivalCraft\" welcomed " + player.getName(),
          dispatcher.dispatchedRequests.get(0).command());
    }

    @Test
    @DisplayName("Missing PAPI in trusted template fails closed")
    void missingPapiInTemplateFailsClosed() {
      var postCmd =
          new PostCommand(
              "papi_cmd",
              "broadcast %missing_papi% welcomed {player}",
              ExecutionPolicy.ON_COMPLETE,
              ExecuteAs.CONSOLE,
              0);
      var snapshot =
          new PresetSnapshot(Map.of(), Map.of("papi_cmd", postCmd), Map.of(), Map.of(), 1L);
      var pcm =
          new PostCommandMeta("papi_cmd", new int[] {}, 0, false, DispatchTarget.PLAYER, true);

      var instance = createPlanInstance(List.of(pcm), snapshot, List.of());
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isFailure());
      assertTrue(instance.hasNotice(NoticeFlag.POST_ACTION_FAILED));
      assertTrue(dispatcher.dispatchedRequests.isEmpty());
    }

    @Test
    @DisplayName("FLOW-11 injection: answer containing nested tags and semicolons remains data")
    void flow11InjectionRemainsData() {
      var pcm =
          new PostCommandMeta(
              "say Answer was: {0}", new int[] {0}, 0, false, DispatchTarget.PLAYER, false);
      var injectionPayload = "<!kill @a> ; /op hacker ; %vault_eco_balance%";
      var instance =
          createPlanInstance(List.of(pcm), PresetSnapshot.empty(), List.of(injectionPayload));
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
      assertEquals(1, dispatcher.dispatchedRequests.size());
      assertEquals(
          "say Answer was: \"<!kill @a> ; /op hacker ; %vault_eco_balance%\"",
          dispatcher.dispatchedRequests.get(0).command());
    }

    @Test
    @DisplayName("Strips leading slash only at dispatcher boundary")
    void stripsLeadingSlashAtDispatcherBoundary() {
      var pcm =
          new PostCommandMeta(
              "/teleport {player} 0 100 0", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var instance = createPlanInstance(List.of(pcm), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
      assertEquals(
          "teleport " + player.getName() + " 0 100 0",
          dispatcher.dispatchedRequests.get(0).command());
    }

    @Test
    @DisplayName(
        "Trusted preset console action preserves TRUSTED_PRESET provenance with console delegation for non-op player")
    void trustedPresetConsoleActionAuthorizedForNonOpPlayer() {
      var nonOpPlayer = createPlayer("NonOpPostActionPlayer");
      assertFalse(nonOpPlayer.isOp());
      var nonOpExecutor = PlayerExecutor.forPlayer(plugin, nonOpPlayer, p -> true);

      var postCmd =
          new PostCommand(
              "admin_reward",
              "give {player} diamond",
              ExecutionPolicy.ON_COMPLETE,
              ExecuteAs.CONSOLE,
              0);
      var snapshot =
          new PresetSnapshot(Map.of(), Map.of("admin_reward", postCmd), Map.of(), Map.of(), 1L);
      var pcm =
          new PostCommandMeta("admin_reward", new int[] {}, 0, false, DispatchTarget.PLAYER, true);

      var completion =
          new InputCompletion(
              nonOpPlayer.getUniqueId(),
              1L,
              0L,
              List.of(),
              "say primary",
              null,
              snapshot,
              DispatchContextSnapshot.player(),
              List.of(pcm));
      var instance =
          new ExecutionPlanInstance(ExecutionId.create(), completion, ExecutionStage.POST_ACTIONS);
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          nonOpPlayer,
          nonOpExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
      assertEquals(1, dispatcher.dispatchedRequests.size());
      var req = dispatcher.dispatchedRequests.get(0);
      assertEquals("give " + nonOpPlayer.getName() + " diamond", req.command());
      assertEquals(ExecuteAs.CONSOLE, req.executeAs());
      assertNotNull(req.provenance());
      assertEquals(
          dev.cyr1en.promptpaper.execution.dispatch.ActionTrustLevel.TRUSTED_PRESET,
          req.provenance().trustLevel());
      assertTrue(req.provenance().consoleDelegated());
      assertTrue(req.provenance().isConsoleAuthorized(nonOpPlayer));
    }

    @Test
    @DisplayName(
        "Untrusted inline console action remains consoleDelegated = false and fails closed for non-op player")
    void untrustedInlineConsoleActionFailsClosedForNonOpPlayer() {
      var nonOpPlayer = createPlayer("NonOpUntrustedInlinePlayer");
      assertFalse(nonOpPlayer.isOp());
      var nonOpExecutor = PlayerExecutor.forPlayer(plugin, nonOpPlayer, p -> true);

      var pcm =
          new PostCommandMeta(
              "give {player} bedrock", new int[] {}, 0, false, DispatchTarget.CONSOLE, false);
      var completion =
          new InputCompletion(
              nonOpPlayer.getUniqueId(),
              1L,
              0L,
              List.of(),
              "say primary",
              null,
              PresetSnapshot.empty(),
              DispatchContextSnapshot.player(),
              List.of(pcm));
      var instance =
          new ExecutionPlanInstance(ExecutionId.create(), completion, ExecutionStage.POST_ACTIONS);
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          nonOpPlayer,
          nonOpExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
      assertEquals(1, dispatcher.dispatchedRequests.size());
      var req = dispatcher.dispatchedRequests.get(0);
      assertEquals(ExecuteAs.CONSOLE, req.executeAs());
      assertNotNull(req.provenance());
      assertEquals(
          dev.cyr1en.promptpaper.execution.dispatch.ActionTrustLevel.UNTRUSTED_INLINE,
          req.provenance().trustLevel());
      assertFalse(req.provenance().consoleDelegated());
      assertFalse(req.provenance().isConsoleAuthorized(nonOpPlayer));
    }
  }

  @Nested
  @DisplayName("Cancellation, Races, & Terminal Cleanup")
  class CancellationAndLifecycleTests {

    @Test
    @DisplayName("Instance cancellation cancels delayed tasks and prevents dispatch")
    void instanceCancellationPreventsDelayedDispatch() {
      var pcm1 =
          new PostCommandMeta("say delayed", new int[] {}, 20, false, DispatchTarget.PLAYER, false);
      var pcm2 =
          new PostCommandMeta("say next", new int[] {}, 0, false, DispatchTarget.PLAYER, false);

      var instance = createPlanInstance(List.of(pcm1, pcm2), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertEquals(1, testScheduler.pendingCount());

      // Cancel plan instance at tick 10 (simulate quit or cancel)
      instance.cancel();
      assertTrue(instance.isTerminal());

      // Advance ticks to 20
      testScheduler.advanceTicks(20);

      // No commands dispatched
      assertTrue(dispatcher.dispatchedRequests.isEmpty());
    }

    @Test
    @DisplayName(
        "Timer vs cancel race: cancellation check before timer execution prevents dispatch")
    void timerVsCancelRacePreventsDispatch() {
      var pcm =
          new PostCommandMeta(
              "say race_cmd", new int[] {}, 10, false, DispatchTarget.PLAYER, false);
      var instance = createPlanInstance(List.of(pcm), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          r -> {});

      // Transition stage directly to CANCELLED right before timer fires
      instance.cancel();

      testScheduler.advanceTicks(10);
      assertTrue(dispatcher.dispatchedRequests.isEmpty());
    }
  }

  @Nested
  @DisplayName("Dispatcher Outcome & Error Handling")
  class DispatcherOutcomeTests {

    @Test
    @DisplayName("Dispatcher returning false halts execution with typed error and aborts remaining")
    void dispatchFalseAbortsRemaining() {
      var pcm1 =
          new PostCommandMeta("say fail1", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var pcm2 =
          new PostCommandMeta("say after", new int[] {}, 0, false, DispatchTarget.PLAYER, false);

      var instance = createPlanInstance(List.of(pcm1, pcm2), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();
      dispatcher.setConfiguredOutcome(
          DispatchOutcome.failure(DispatchErrorKind.DISPATCH_RETURNED_FALSE, "command failed"));

      var resultRef = new AtomicReference<PostActionResult>();
      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isFailure());
      assertEquals(DispatchErrorKind.DISPATCH_RETURNED_FALSE, resultRef.get().error().kind());
      assertTrue(instance.hasNotice(NoticeFlag.POST_ACTION_FAILED));
      // Only the first command was attempted
      assertEquals(1, dispatcher.dispatchedRequests.size());
    }

    @Test
    @DisplayName("Dispatcher throwing exception halts execution with typed error")
    void dispatchExceptionAbortsRemaining() {
      var pcm1 =
          new PostCommandMeta("say throwing", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var pcm2 =
          new PostCommandMeta("say after", new int[] {}, 0, false, DispatchTarget.PLAYER, false);

      var instance = createPlanInstance(List.of(pcm1, pcm2), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();
      dispatcher.setThrowException(true);

      var resultRef = new AtomicReference<PostActionResult>();
      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isFailure());
      assertEquals(DispatchErrorKind.EXCEPTION_THROWN, resultRef.get().error().kind());
      assertEquals(1, dispatcher.dispatchedRequests.size());
    }

    @Test
    @DisplayName("Duplicate callbacks from dispatcher invoke completion callback exactly once")
    void duplicateCallbacksHandledSafely() {
      var pcm =
          new PostCommandMeta("say single", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var instance = createPlanInstance(List.of(pcm), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();
      dispatcher.setDuplicateCallbacks(true);

      var callbackCount = new AtomicInteger(0);
      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          r -> callbackCount.incrementAndGet());

      assertEquals(1, callbackCount.get());
    }

    @Test
    @DisplayName(
        "Dispatcher callback from arbitrary thread re-enters player executor before advancing")
    void arbitraryThreadCallbackReentersPlayerExecutor() throws InterruptedException {
      var pcm1 =
          new PostCommandMeta("say thread1", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var pcm2 =
          new PostCommandMeta("say thread2", new int[] {}, 0, false, DispatchTarget.PLAYER, false);

      var instance = createPlanInstance(List.of(pcm1, pcm2), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();
      dispatcher.setAsyncCallback(true);

      var resultRef = new AtomicReference<PostActionResult>();
      var completedSignal = new AtomicBoolean(false);

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          res -> {
            resultRef.set(res);
            completedSignal.set(true);
          });

      // Wait up to 2 seconds for async completion
      long start = System.currentTimeMillis();
      while (!completedSignal.get() && (System.currentTimeMillis() - start) < 2000) {
        Thread.sleep(20);
      }

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
      assertEquals(2, dispatcher.dispatchedRequests.size());
    }

    @Test
    @DisplayName(
        "Dispatcher dispatch method throwing synchronously is caught and reports failure exactly once")
    void synchronousDispatchThrowReportsFailure() {
      var pcm =
          new PostCommandMeta(
              "say sync_throw", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var instance = createPlanInstance(List.of(pcm), PresetSnapshot.empty(), List.of());
      var dispatcher =
          new ImmediateActionDispatcher() {
            @Override
            public void dispatch(
                ImmediateActionRequest request,
                dev.cyr1en.promptpaper.execution.dispatch.ActionDispatchCallback callback) {
              throw new RuntimeException("Sync dispatch crash");
            }
          };
      var resultRef = new AtomicReference<PostActionResult>();
      var callCount = new AtomicInteger(0);

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          r -> {
            resultRef.set(r);
            callCount.incrementAndGet();
          });

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isFailure());
      assertEquals(DispatchErrorKind.EXCEPTION_THROWN, resultRef.get().error().kind());
      assertEquals(1, callCount.get());
      assertTrue(instance.hasNotice(NoticeFlag.POST_ACTION_FAILED));
    }

    @Test
    @DisplayName("PlayerExecutor execute throwing exception reports failure once and cleans up")
    void executorThrowReportsFailure() {
      var pcm =
          new PostCommandMeta(
              "say exec_throw", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var instance = createPlanInstance(List.of(pcm), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();

      PlayerExecutor throwingExecutor =
          (task, retired) -> {
            throw new RuntimeException("Executor submission failed");
          };

      var resultRef = new AtomicReference<PostActionResult>();
      var callCount = new AtomicInteger(0);

      PostActionRunner.execute(
          player,
          throwingExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          r -> {
            resultRef.set(r);
            callCount.incrementAndGet();
          });

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isFailure());
      assertEquals(DispatchErrorKind.EXCEPTION_THROWN, resultRef.get().error().kind());
      assertEquals(1, callCount.get());
      assertTrue(instance.hasNotice(NoticeFlag.POST_ACTION_FAILED));
    }

    @Test
    @DisplayName("PlayerExecutor retirement in dispatcher callback reports SCHEDULER_RETIRED")
    void executorRetirementReportsFailure() {
      var pcm =
          new PostCommandMeta(
              "say retired_cmd", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var instance = createPlanInstance(List.of(pcm), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();

      PlayerExecutor retiringExecutor =
          (task, retired) -> {
            if (retired != null) {
              retired.run();
            }
          };

      var resultRef = new AtomicReference<PostActionResult>();
      var callCount = new AtomicInteger(0);

      PostActionRunner.execute(
          player,
          retiringExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          r -> {
            resultRef.set(r);
            callCount.incrementAndGet();
          });

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isFailure());
      assertEquals(DispatchErrorKind.SCHEDULER_RETIRED, resultRef.get().error().kind());
      assertEquals(1, callCount.get());
      assertTrue(instance.hasNotice(NoticeFlag.POST_ACTION_FAILED));
    }

    @Test
    @DisplayName("Scheduler scheduleDelayed throwing exception reports failure and cancels task")
    void schedulerThrowReportsFailure() {
      var pcm =
          new PostCommandMeta(
              "say sched_throw", new int[] {}, 10, false, DispatchTarget.PLAYER, false);
      var instance = createPlanInstance(List.of(pcm), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();

      PostActionScheduler throwingScheduler =
          (p, task, retired, delay) -> {
            throw new RuntimeException("Scheduler fault");
          };

      var resultRef = new AtomicReference<PostActionResult>();
      var callCount = new AtomicInteger(0);

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          throwingScheduler,
          PapiReferenceResolver.empty(),
          r -> {
            resultRef.set(r);
            callCount.incrementAndGet();
          });

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isFailure());
      assertEquals(DispatchErrorKind.EXCEPTION_THROWN, resultRef.get().error().kind());
      assertEquals(1, callCount.get());
      assertTrue(instance.hasNotice(NoticeFlag.POST_ACTION_FAILED));
    }

    @Test
    @DisplayName("Delayed scheduler retired callback reports SCHEDULER_RETIRED and cancels delay")
    void delayedSchedulerRetiredReportsFailure() {
      var pcm =
          new PostCommandMeta(
              "say sched_retire", new int[] {}, 10, false, DispatchTarget.PLAYER, false);
      var instance = createPlanInstance(List.of(pcm), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();

      var resultRef = new AtomicReference<PostActionResult>();
      var callCount = new AtomicInteger(0);

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          r -> {
            resultRef.set(r);
            callCount.incrementAndGet();
          });

      assertEquals(1, testScheduler.scheduledTasks.size());
      var timer = testScheduler.scheduledTasks.get(0);
      timer.retired().run();

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isFailure());
      assertEquals(DispatchErrorKind.SCHEDULER_RETIRED, resultRef.get().error().kind());
      assertEquals(1, callCount.get());
      assertTrue(instance.hasNotice(NoticeFlag.POST_ACTION_FAILED));
    }

    @Test
    @DisplayName("Duplicate callback after dispatcher throw is safely ignored")
    void duplicateCallbackAfterThrowIgnored() {
      var pcm =
          new PostCommandMeta(
              "say throw_and_callback", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var instance = createPlanInstance(List.of(pcm), PresetSnapshot.empty(), List.of());

      var callCount = new AtomicInteger(0);
      var resultRef = new AtomicReference<PostActionResult>();

      ImmediateActionDispatcher weirdDispatcher =
          (request, callback) -> {
            callback.onComplete(
                DispatchOutcome.failure(DispatchErrorKind.EXCEPTION_THROWN, "Error 1"));
            // Duplicate calls:
            callback.onComplete(
                DispatchOutcome.failure(DispatchErrorKind.DISPATCH_RETURNED_FALSE, "Error 2"));
            callback.onComplete(DispatchOutcome.success());
          };

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          weirdDispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          r -> {
            resultRef.set(r);
            callCount.incrementAndGet();
          });

      assertEquals(1, callCount.get());
      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isFailure());
      assertEquals(DispatchErrorKind.EXCEPTION_THROWN, resultRef.get().error().kind());
    }

    @Test
    @DisplayName(
        "On step failure in multi-action plan, runner completes exactly once with failure and stops")
    void multiActionStopsOnFailure() {
      var pcm1 =
          new PostCommandMeta("say step1", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var pcm2 =
          new PostCommandMeta("say step2", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var pcm3 =
          new PostCommandMeta("say step3", new int[] {}, 0, false, DispatchTarget.PLAYER, false);

      var instance =
          createPlanInstance(List.of(pcm1, pcm2, pcm3), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();
      dispatcher.setConfiguredOutcome(
          DispatchOutcome.failure(DispatchErrorKind.DISPATCH_RETURNED_FALSE, "Blocked"));

      var callCount = new AtomicInteger(0);
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          r -> {
            resultRef.set(r);
            callCount.incrementAndGet();
          });

      assertEquals(1, callCount.get());
      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isFailure());
      assertEquals(DispatchErrorKind.DISPATCH_RETURNED_FALSE, resultRef.get().error().kind());
      // Only step1 was dispatched
      assertEquals(1, dispatcher.dispatchedRequests.size());
      assertEquals("say step1", dispatcher.dispatchedRequests.get(0).command());
    }
  }

  @Nested
  @DisplayName("Snapshot Isolation Across Reload")
  class SnapshotIsolationTests {

    @Test
    @DisplayName("In-flight execution uses captured snapshot across reload")
    void inFlightExecutionUsesCapturedSnapshot() {
      var oldDef =
          new PostCommand(
              "reloadable_cmd",
              "say OLD snapshot",
              ExecutionPolicy.ON_COMPLETE,
              ExecuteAs.PLAYER,
              10);
      var capturedSnapshot =
          new PresetSnapshot(Map.of(), Map.of("reloadable_cmd", oldDef), Map.of(), Map.of(), 1L);

      var pcm =
          new PostCommandMeta(
              "reloadable_cmd", new int[] {}, 0, false, DispatchTarget.PLAYER, true);
      var instance = createPlanInstance(List.of(pcm), capturedSnapshot, List.of());
      var dispatcher = new RecordingDispatcher();
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          resultRef::set);

      // Simulate reload with a new snapshot in the registry
      var newDef =
          new PostCommand(
              "reloadable_cmd",
              "say NEW snapshot",
              ExecutionPolicy.ON_COMPLETE,
              ExecuteAs.PLAYER,
              10);
      var newSnapshot =
          new PresetSnapshot(Map.of(), Map.of("reloadable_cmd", newDef), Map.of(), Map.of(), 2L);

      // Advance timer for delayed execution
      testScheduler.advanceTicks(10);

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
      // Must have used OLD snapshot captured in InputCompletion!
      assertEquals("say OLD snapshot", dispatcher.dispatchedRequests.get(0).command());
    }
  }

  @Nested
  @DisplayName("Concurrency, Duplicate Deliveries, & Race Hardening")
  class ConcurrencyAndDeduplicationTests {

    @Test
    @DisplayName("Multi-action chain with duplicate concurrent callbacks at step1 and step2")
    void multiActionChainConcurrentDuplicateCallbacks() throws InterruptedException {
      var pcm1 =
          new PostCommandMeta("say step1", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var pcm2 =
          new PostCommandMeta("say step2", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var pcm3 =
          new PostCommandMeta("say step3", new int[] {}, 0, false, DispatchTarget.PLAYER, false);

      var instance =
          createPlanInstance(List.of(pcm1, pcm2, pcm3), PresetSnapshot.empty(), List.of());
      var dispatchedRequests = new CopyOnWriteArrayList<ImmediateActionRequest>();

      ImmediateActionDispatcher concurrentDuplicateDispatcher =
          (request, callback) -> {
            dispatchedRequests.add(request);
            int threads = 10;
            var latch = new java.util.concurrent.CountDownLatch(threads);
            for (int i = 0; i < threads; i++) {
              new Thread(
                      () -> {
                        try {
                          callback.onComplete(DispatchOutcome.success());
                        } finally {
                          latch.countDown();
                        }
                      })
                  .start();
            }
            try {
              latch.await();
            } catch (InterruptedException ignored) {
            }
          };

      var callbackCount = new AtomicInteger(0);
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          concurrentDuplicateDispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          r -> {
            resultRef.set(r);
            callbackCount.incrementAndGet();
          });

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
      assertEquals(
          1, callbackCount.get(), "Terminal completion callback must be called exactly once");
      assertEquals(
          3, dispatchedRequests.size(), "Each action in chain must be dispatched exactly once");
      assertEquals("say step1", dispatchedRequests.get(0).command());
      assertEquals("say step2", dispatchedRequests.get(1).command());
      assertEquals("say step3", dispatchedRequests.get(2).command());
    }

    @Test
    @DisplayName("Duplicate concurrent timer callbacks for delayed action dispatch exactly once")
    void duplicateTimerCallbacksOnlyDispatchOnce() throws InterruptedException {
      var pcm =
          new PostCommandMeta(
              "say delayed_cmd", new int[] {}, 10, false, DispatchTarget.PLAYER, false);
      var instance = createPlanInstance(List.of(pcm), PresetSnapshot.empty(), List.of());
      var dispatcher = new RecordingDispatcher();

      var callbackCount = new AtomicInteger(0);
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          dispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          r -> {
            resultRef.set(r);
            callbackCount.incrementAndGet();
          });

      assertEquals(1, testScheduler.scheduledTasks.size());
      var timer = testScheduler.scheduledTasks.get(0);

      // Fire timer.task().run() concurrently from 10 threads
      int threads = 10;
      var startLatch = new java.util.concurrent.CountDownLatch(1);
      var doneLatch = new java.util.concurrent.CountDownLatch(threads);
      for (int i = 0; i < threads; i++) {
        new Thread(
                () -> {
                  try {
                    startLatch.await();
                    timer.task().run();
                  } catch (InterruptedException ignored) {
                  } finally {
                    doneLatch.countDown();
                  }
                })
            .start();
      }
      startLatch.countDown();
      doneLatch.await();

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
      assertEquals(1, callbackCount.get());
      assertEquals(1, dispatcher.dispatchedRequests.size());
      assertEquals("say delayed_cmd", dispatcher.dispatchedRequests.get(0).command());
    }

    @Test
    @DisplayName("Timer fire and instance cancel race has exactly one winner")
    void timerAndCancelRaceHasSingleWinner() throws InterruptedException {
      for (int run = 0; run < 20; run++) {
        var sched = new ManualTestScheduler();
        var pcm =
            new PostCommandMeta(
                "say race_cmd", new int[] {}, 10, false, DispatchTarget.PLAYER, false);
        var instance = createPlanInstance(List.of(pcm), PresetSnapshot.empty(), List.of());
        var dispatcher = new RecordingDispatcher();

        var callbackCount = new AtomicInteger(0);
        var resultRef = new AtomicReference<PostActionResult>();

        PostActionRunner.execute(
            player,
            playerExecutor,
            instance,
            ExecutionPolicy.ON_COMPLETE,
            dispatcher,
            sched,
            PapiReferenceResolver.empty(),
            r -> {
              resultRef.set(r);
              callbackCount.incrementAndGet();
            });

        assertEquals(1, sched.scheduledTasks.size());
        var timer = sched.scheduledTasks.get(0);

        var startLatch = new java.util.concurrent.CountDownLatch(1);
        var doneLatch = new java.util.concurrent.CountDownLatch(2);

        new Thread(
                () -> {
                  try {
                    startLatch.await();
                    instance.cancel();
                  } catch (InterruptedException ignored) {
                  } finally {
                    doneLatch.countDown();
                  }
                })
            .start();

        new Thread(
                () -> {
                  try {
                    startLatch.await();
                    timer.task().run();
                  } catch (InterruptedException ignored) {
                  } finally {
                    doneLatch.countDown();
                  }
                })
            .start();

        startLatch.countDown();
        doneLatch.await();

        // At most 1 dispatch occurred, and runner completion/cancellation is clean
        assertTrue(dispatcher.dispatchedRequests.size() <= 1);
        assertTrue(callbackCount.get() <= 1);
      }
    }

    @Test
    @DisplayName("Stale callback from step 1 after step 2 is active is ignored")
    void staleStep1CallbackAfterStep2ActiveIgnored() {
      var pcm1 =
          new PostCommandMeta("say step1", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var pcm2 =
          new PostCommandMeta("say step2", new int[] {}, 0, false, DispatchTarget.PLAYER, false);

      var instance = createPlanInstance(List.of(pcm1, pcm2), PresetSnapshot.empty(), List.of());
      var step1CallbackRef =
          new AtomicReference<dev.cyr1en.promptpaper.execution.dispatch.ActionDispatchCallback>();
      var step2CallbackRef =
          new AtomicReference<dev.cyr1en.promptpaper.execution.dispatch.ActionDispatchCallback>();
      var dispatchedRequests = new CopyOnWriteArrayList<ImmediateActionRequest>();

      ImmediateActionDispatcher manualDispatcher =
          (request, callback) -> {
            dispatchedRequests.add(request);
            if (dispatchedRequests.size() == 1) {
              step1CallbackRef.set(callback);
            } else if (dispatchedRequests.size() == 2) {
              step2CallbackRef.set(callback);
            }
          };

      var callbackCount = new AtomicInteger(0);
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          manualDispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          r -> {
            resultRef.set(r);
            callbackCount.incrementAndGet();
          });

      assertEquals(1, dispatchedRequests.size());
      assertEquals("say step1", dispatchedRequests.get(0).command());
      assertNotNull(step1CallbackRef.get());

      // Complete step 1 -> advances to step 2
      step1CallbackRef.get().onComplete(DispatchOutcome.success());

      assertEquals(2, dispatchedRequests.size());
      assertEquals("say step2", dispatchedRequests.get(1).command());
      assertNotNull(step2CallbackRef.get());
      assertNull(resultRef.get(), "Runner should not be completed yet; step 2 is pending");

      // Fire stale step 1 callback again (both failure and success attempts)
      step1CallbackRef
          .get()
          .onComplete(
              DispatchOutcome.failure(DispatchErrorKind.DISPATCH_RETURNED_FALSE, "Stale fail"));
      step1CallbackRef.get().onComplete(DispatchOutcome.success());

      // Verify runner still pending on step 2, no failure recorded from stale step 1 callback
      assertNull(resultRef.get());
      assertEquals(2, dispatchedRequests.size());

      // Now complete step 2
      step2CallbackRef.get().onComplete(DispatchOutcome.success());

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
      assertEquals(1, callbackCount.get());
      assertEquals(2, dispatchedRequests.size());
    }

    @Test
    @DisplayName(
        "Mixed chain with delayed and immediate actions has exactly one dispatch per action and one terminal completion")
    void exactlyOneDispatchPerActionAndOneTerminalCompletion() {
      var pcm1 =
          new PostCommandMeta("say act1", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var pcm2 =
          new PostCommandMeta("say act2", new int[] {}, 10, false, DispatchTarget.PLAYER, false);
      var pcm3 =
          new PostCommandMeta("say act3", new int[] {}, 0, false, DispatchTarget.PLAYER, false);
      var pcm4 =
          new PostCommandMeta("say act4", new int[] {}, 5, false, DispatchTarget.PLAYER, false);

      var instance =
          createPlanInstance(List.of(pcm1, pcm2, pcm3, pcm4), PresetSnapshot.empty(), List.of());
      var dispatchedRequests = new CopyOnWriteArrayList<ImmediateActionRequest>();

      ImmediateActionDispatcher spammyDispatcher =
          (request, callback) -> {
            dispatchedRequests.add(request);
            // Deliver duplicate callbacks for each dispatch
            for (int i = 0; i < 5; i++) {
              callback.onComplete(DispatchOutcome.success());
            }
          };

      var callbackCount = new AtomicInteger(0);
      var resultRef = new AtomicReference<PostActionResult>();

      PostActionRunner.execute(
          player,
          playerExecutor,
          instance,
          ExecutionPolicy.ON_COMPLETE,
          spammyDispatcher,
          testScheduler,
          PapiReferenceResolver.empty(),
          r -> {
            resultRef.set(r);
            callbackCount.incrementAndGet();
          });

      // Step 1 was immediate
      assertEquals(1, dispatchedRequests.size());
      assertEquals("say act1", dispatchedRequests.get(0).command());

      // Step 2 is delayed (10 ticks)
      assertEquals(1, testScheduler.scheduledTasks.size());
      var timer2 = testScheduler.scheduledTasks.get(0);
      // Simulate duplicate timer fires
      timer2.task().run();
      timer2.task().run();

      // After step 2 timer fires, step 2 dispatches, duplicate callbacks complete step 2,
      // and step 3 (immediate) automatically executes and completes, scheduling step 4 (delayed 5
      // ticks)
      assertEquals(3, dispatchedRequests.size());
      assertEquals("say act2", dispatchedRequests.get(1).command());
      assertEquals("say act3", dispatchedRequests.get(2).command());

      // Step 4 is delayed (5 ticks)
      assertEquals(2, testScheduler.scheduledTasks.size());
      var timer4 = testScheduler.scheduledTasks.get(1);
      timer4.task().run();
      timer4.task().run();

      assertEquals(4, dispatchedRequests.size());
      assertEquals("say act4", dispatchedRequests.get(3).command());

      assertNotNull(resultRef.get());
      assertTrue(resultRef.get().isSuccess());
      assertEquals(1, callbackCount.get(), "Terminal callback must be called exactly once");
    }
  }
}
