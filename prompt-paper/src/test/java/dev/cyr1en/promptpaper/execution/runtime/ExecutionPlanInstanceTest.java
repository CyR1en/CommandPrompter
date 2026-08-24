package dev.cyr1en.promptpaper.execution.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import dev.cyr1en.promptcore.plan.ExecutionPlanDefinition;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import dev.cyr1en.promptpaper.util.CancellableTask;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecutionPlanInstanceTest {

  private UUID initiator;
  private ExecutionId executionId;
  private InputCompletion completion;
  private ExecutionPlanInstance instance;

  @BeforeEach
  void setUp() {
    initiator = UUID.randomUUID();
    executionId = ExecutionId.create();
    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(TemplateCompiler.compile("say hello"), List.of(), List.of());
    completion =
        new InputCompletion(
            initiator,
            1,
            0L,
            List.of("hello"),
            "say hello",
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());
    instance = new ExecutionPlanInstance(executionId, completion);
  }

  @Test
  @DisplayName("Initial stage is PRE_DISPATCH_GATES")
  void initialStage() {
    assertEquals(ExecutionStage.PRE_DISPATCH_GATES, instance.getStage());
    assertFalse(instance.isTerminal());
    assertEquals(executionId, instance.getExecutionId());
    assertEquals(initiator, instance.getInitiatorUuid());
    assertEquals(1, instance.getIncarnation());
    assertTrue(instance.getCreatedAtMillis() > 0);
  }

  @Test
  @DisplayName("Legal stage transitions succeed")
  void legalStageTransitions() {
    // PRE_DISPATCH_GATES -> PRIMARY_DISPATCH
    assertTrue(instance.tryTransitionTo(ExecutionStage.PRIMARY_DISPATCH));
    assertEquals(ExecutionStage.PRIMARY_DISPATCH, instance.getStage());

    // PRIMARY_DISPATCH -> POST_ACTIONS
    instance.transitionTo(ExecutionStage.POST_ACTIONS);
    assertEquals(ExecutionStage.POST_ACTIONS, instance.getStage());

    // POST_ACTIONS -> COMPLETED
    instance.transitionTo(ExecutionStage.COMPLETED);
    assertEquals(ExecutionStage.COMPLETED, instance.getStage());
    assertTrue(instance.isTerminal());

    // Terminal stage cannot transition
    assertFalse(instance.tryTransitionTo(ExecutionStage.CANCELLED));
    assertThrows(
        IllegalStateException.class, () -> instance.transitionTo(ExecutionStage.CANCELLED));
  }

  @Test
  @DisplayName("Illegal stage transitions fail and throw")
  void illegalStageTransitions() {
    // Cannot transition directly from PRE_DISPATCH_GATES to COMPLETED
    assertFalse(instance.tryTransitionTo(ExecutionStage.COMPLETED));
    assertEquals(ExecutionStage.PRE_DISPATCH_GATES, instance.getStage());

    assertThrows(
        IllegalStateException.class, () -> instance.transitionTo(ExecutionStage.COMPLETED));
  }

  @Test
  @DisplayName("claimPrimaryExecution succeeds exactly once")
  void claimPrimaryExecutionOnce() {
    assertFalse(instance.isPrimaryClaimed());

    assertTrue(instance.claimPrimaryExecution());
    assertTrue(instance.isPrimaryClaimed());

    // Second claim fails
    assertFalse(instance.claimPrimaryExecution());
  }

  @Test
  @DisplayName("claimPrimaryExecution fails when in POST_ACTIONS or terminal stage")
  void claimPrimaryInPostActionsOrTerminalFails() {
    instance.transitionTo(ExecutionStage.POST_ACTIONS);
    assertFalse(instance.claimPrimaryExecution());

    instance.transitionTo(ExecutionStage.CANCELLED);
    assertFalse(instance.claimPrimaryExecution());
  }

  @Test
  @DisplayName("Verification matches correct caller and rejects mismatched attributes")
  void verification() {
    assertTrue(instance.verify(initiator, 1, executionId));
    assertTrue(instance.verifyCaller(initiator, 1));

    // Wrong initiator
    assertFalse(instance.verify(UUID.randomUUID(), 1, executionId));
    assertFalse(instance.verifyCaller(UUID.randomUUID(), 1));

    // Wrong incarnation
    assertFalse(instance.verify(initiator, 2, executionId));
    assertFalse(instance.verifyCaller(initiator, 2));

    // Wrong execution ID
    assertFalse(instance.verify(initiator, 1, ExecutionId.create()));
  }

  @Test
  @DisplayName("Notice flags record once (log-once semantics)")
  void noticeFlagLogOnce() {
    assertFalse(instance.hasNotice(NoticeFlag.PERMISSION_MISSING));

    assertTrue(instance.recordNotice(NoticeFlag.PERMISSION_MISSING));
    assertTrue(instance.hasNotice(NoticeFlag.PERMISSION_MISSING));

    // Second attempt returns false
    assertFalse(instance.recordNotice(NoticeFlag.PERMISSION_MISSING));

    // Different flag succeeds
    assertTrue(instance.recordNotice(NoticeFlag.GATE_TIMEOUT));

    Set<NoticeFlag> notices = instance.getNotices();
    assertEquals(2, notices.size());
    assertTrue(notices.contains(NoticeFlag.PERMISSION_MISSING));
    assertTrue(notices.contains(NoticeFlag.GATE_TIMEOUT));
  }

  @Test
  @DisplayName("Registered cancellable tasks are cancelled on terminal transition")
  void cancellableTasksCancelledOnTerminal() {
    AtomicBoolean taskCancelled = new AtomicBoolean(false);
    CancellableTask task = () -> taskCancelled.set(true);

    instance.registerCancellable(task);
    assertFalse(taskCancelled.get());

    instance.transitionTo(ExecutionStage.CANCELLED);
    assertTrue(taskCancelled.get());
  }

  @Test
  @DisplayName("Registering cancellable on already-terminal instance cancels immediately")
  void registerCancellableOnTerminalCancelsImmediately() {
    instance.cancel();
    assertTrue(instance.isTerminal());

    AtomicBoolean taskCancelled = new AtomicBoolean(false);
    instance.registerCancellable(() -> taskCancelled.set(true));
    assertTrue(taskCancelled.get());
  }

  @Test
  @DisplayName("Cleanup hooks run exactly once on terminal transition")
  void cleanupHooksRunOnceOnTerminal() {
    AtomicInteger hookExecutions = new AtomicInteger(0);
    instance.registerCleanupHook(hookExecutions::incrementAndGet);

    assertEquals(0, hookExecutions.get());

    instance.transitionTo(ExecutionStage.PRIMARY_DISPATCH);
    instance.transitionTo(ExecutionStage.POST_ACTIONS);
    instance.transitionTo(ExecutionStage.COMPLETED);
    assertEquals(1, hookExecutions.get());

    // Calling cancel or terminal cleanup again does not re-run hook
    instance.runTerminalCleanup();
    assertEquals(1, hookExecutions.get());

    // Registering hook on already-cleaned-up instance runs it immediately
    AtomicInteger lateHook = new AtomicInteger(0);
    instance.registerCleanupHook(lateHook::incrementAndGet);
    assertEquals(1, lateHook.get());
  }

  @Test
  @DisplayName("Initial terminal stage runs cleanup immediately")
  void initialTerminalStageRunsCleanup() {
    ExecutionPlanInstance terminalInstance =
        new ExecutionPlanInstance(executionId, completion, ExecutionStage.COMPLETED);
    assertTrue(terminalInstance.isTerminal());
    assertTrue(terminalInstance.isCleanedUp());

    AtomicInteger hook = new AtomicInteger(0);
    terminalInstance.registerCleanupHook(hook::incrementAndGet);
    assertEquals(1, hook.get());

    AtomicBoolean cancelled = new AtomicBoolean(false);
    terminalInstance.registerCancellable(() -> cancelled.set(true));
    assertTrue(cancelled.get());

    assertFalse(terminalInstance.claimPrimaryExecution());
  }

  @Test
  @DisplayName("Exceptions in cleanup hooks or cancellables do not prevent remaining items from running")
  void cleanupToleratesExceptions() {
    AtomicBoolean secondTaskCancelled = new AtomicBoolean(false);
    AtomicBoolean secondHookRan = new AtomicBoolean(false);

    instance.registerCancellable(
        () -> {
          throw new RuntimeException("boom");
        });
    instance.registerCancellable(() -> secondTaskCancelled.set(true));

    instance.registerCleanupHook(
        () -> {
          throw new RuntimeException("boom hook");
        });
    instance.registerCleanupHook(() -> secondHookRan.set(true));

    instance.cancel();

    assertTrue(secondTaskCancelled.get());
    assertTrue(secondHookRan.get());
    assertTrue(instance.isCleanedUp());
  }

  @Test
  @DisplayName("cancel() is idempotent and transitions to CANCELLED")
  void cancelIdempotency() {
    assertTrue(instance.cancel());
    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
    assertTrue(instance.isTerminal());

    // Subsequent cancel returns false
    assertFalse(instance.cancel());
    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
  }
}
