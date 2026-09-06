package dev.cyr1en.promptpaper.execution.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import dev.cyr1en.promptcore.plan.ExecutionPlanDefinition;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import dev.cyr1en.promptpaper.util.CancellableTask;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class RuntimeStateConcurrencyTest {

  private ExecutionPlanInstance createInstance(UUID initiator, int incarnation) {
    ExecutionId executionId = ExecutionId.create();
    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(TemplateCompiler.compile("test cmd"), List.of(), List.of());
    InputCompletion completion =
        new InputCompletion(
            initiator,
            incarnation,
            0L,
            List.of("ans"),
            "test cmd",
            plan,
            PresetSnapshot.empty(),
            DispatchContextSnapshot.player());
    return new ExecutionPlanInstance(executionId, completion);
  }

  @Test
  @DisplayName("Racing claimPrimaryExecution grants claim to exactly one thread")
  void racingPrimaryExecutionClaim() throws Exception {
    int threadCount = 32;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    try {
      ExecutionPlanInstance instance = createInstance(UUID.randomUUID(), 1);
      CountDownLatch readyLatch = new CountDownLatch(threadCount);
      CountDownLatch startLatch = new CountDownLatch(1);
      AtomicInteger successfulClaims = new AtomicInteger(0);

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < threadCount; i++) {
        futures.add(
            executor.submit(
                () -> {
                  readyLatch.countDown();
                  try {
                    startLatch.await();
                    if (instance.claimPrimaryExecution()) {
                      successfulClaims.incrementAndGet();
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                }));
      }

      readyLatch.await(5, TimeUnit.SECONDS);
      startLatch.countDown();

      for (Future<?> f : futures) {
        f.get(5, TimeUnit.SECONDS);
      }

      assertEquals(1, successfulClaims.get(), "Exactly one thread must claim primary execution");
      assertTrue(instance.isPrimaryClaimed());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName("Concurrent registration for the same player allows exactly one winner")
  void racingRegistrationSamePlayer() throws Exception {
    int threadCount = 32;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    try {
      ExecutionRegistry registry = new ExecutionRegistry();
      UUID playerId = UUID.randomUUID();

      CountDownLatch readyLatch = new CountDownLatch(threadCount);
      CountDownLatch startLatch = new CountDownLatch(1);
      AtomicInteger successfulRegistrations = new AtomicInteger(0);
      AtomicInteger rejectedRegistrations = new AtomicInteger(0);

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < threadCount; i++) {
        final int incarnation = i + 1;
        futures.add(
            executor.submit(
                () -> {
                  ExecutionPlanInstance instance = createInstance(playerId, incarnation);
                  readyLatch.countDown();
                  try {
                    startLatch.await();
                    var result = registry.register(instance);
                    if (result instanceof ExecutionRegistry.RegistrationResult.Success) {
                      successfulRegistrations.incrementAndGet();
                    } else if (result
                        instanceof ExecutionRegistry.RegistrationResult.RejectedAlreadyActive) {
                      rejectedRegistrations.incrementAndGet();
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                }));
      }

      readyLatch.await(5, TimeUnit.SECONDS);
      startLatch.countDown();

      for (Future<?> f : futures) {
        f.get(5, TimeUnit.SECONDS);
      }

      assertEquals(1, successfulRegistrations.get(), "Exactly one registration must succeed");
      assertEquals(threadCount - 1, rejectedRegistrations.get());
      assertEquals(1, registry.size());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName("Concurrent cleanup and cancellation execute hooks exactly once")
  void concurrentCleanupExecution() throws Exception {
    int threadCount = 32;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    try {
      ExecutionPlanInstance instance = createInstance(UUID.randomUUID(), 1);
      AtomicInteger hookExecutions = new AtomicInteger(0);
      instance.registerCleanupHook(hookExecutions::incrementAndGet);

      CountDownLatch readyLatch = new CountDownLatch(threadCount);
      CountDownLatch startLatch = new CountDownLatch(1);

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < threadCount; i++) {
        final int mode = i % 2;
        futures.add(
            executor.submit(
                () -> {
                  readyLatch.countDown();
                  try {
                    startLatch.await();
                    if (mode == 0) {
                      instance.cancel();
                    } else {
                      instance.tryTransitionTo(ExecutionStage.CANCELLED);
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                }));
      }

      readyLatch.await(5, TimeUnit.SECONDS);
      startLatch.countDown();

      for (Future<?> f : futures) {
        f.get(5, TimeUnit.SECONDS);
      }

      assertTrue(instance.isTerminal());
      assertEquals(1, hookExecutions.get(), "Cleanup hook must execute exactly once");
    } finally {
      executor.shutdownNow();
    }
  }

  @RepeatedTest(20)
  @DisplayName(
      "Racing dispatch claim vs cancellation guarantees at most one claim and consistent terminal state")
  void racingDispatchVsCancel() throws Exception {
    int threadCount = 16;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    try {
      ExecutionPlanInstance instance = createInstance(UUID.randomUUID(), 1);
      CountDownLatch readyLatch = new CountDownLatch(threadCount);
      CountDownLatch startLatch = new CountDownLatch(1);
      AtomicInteger successfulClaims = new AtomicInteger(0);
      AtomicInteger successfulCancels = new AtomicInteger(0);

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < threadCount; i++) {
        final int index = i;
        futures.add(
            executor.submit(
                () -> {
                  readyLatch.countDown();
                  try {
                    startLatch.await();
                    if (index % 2 == 0) {
                      instance.tryTransitionTo(ExecutionStage.PRIMARY_DISPATCH);
                      if (instance.claimPrimaryExecution()) {
                        successfulClaims.incrementAndGet();
                      }
                    } else {
                      if (instance.cancel()) {
                        successfulCancels.incrementAndGet();
                      }
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                }));
      }

      readyLatch.await(5, TimeUnit.SECONDS);
      startLatch.countDown();

      for (Future<?> f : futures) {
        f.get(5, TimeUnit.SECONDS);
      }

      assertTrue(
          successfulClaims.get() <= 1,
          "Primary claim must succeed at most once under concurrent dispatch vs cancel");
      assertTrue(
          instance.isTerminal(),
          "Instance must reach a terminal stage after concurrent cancellation");
      assertTrue(
          instance.isCleanedUp(), "Instance must be cleaned up when reaching a terminal stage");
    } finally {
      executor.shutdownNow();
    }
  }

  @RepeatedTest(20)
  @DisplayName(
      "Racing cleanup hook registration vs cleanup execution executes each hook exactly once")
  void racingHookRegistrationVsCleanup() throws Exception {
    int registerThreads = 32;
    int cleanupThreads = 8;
    int totalThreads = registerThreads + cleanupThreads;
    ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
    try {
      ExecutionPlanInstance instance = createInstance(UUID.randomUUID(), 1);
      AtomicInteger[] hookCounters = new AtomicInteger[registerThreads];
      for (int i = 0; i < registerThreads; i++) {
        hookCounters[i] = new AtomicInteger(0);
      }

      CountDownLatch readyLatch = new CountDownLatch(totalThreads);
      CountDownLatch startLatch = new CountDownLatch(1);

      List<Future<?>> futures = new ArrayList<>();

      // Registration threads
      for (int i = 0; i < registerThreads; i++) {
        final int hookIndex = i;
        futures.add(
            executor.submit(
                () -> {
                  readyLatch.countDown();
                  try {
                    startLatch.await();
                    instance.registerCleanupHook(hookCounters[hookIndex]::incrementAndGet);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                }));
      }

      // Cleanup threads
      for (int i = 0; i < cleanupThreads; i++) {
        final int mode = i % 2;
        futures.add(
            executor.submit(
                () -> {
                  readyLatch.countDown();
                  try {
                    startLatch.await();
                    if (mode == 0) {
                      instance.cancel();
                    } else {
                      instance.tryTransitionTo(ExecutionStage.COMPLETED);
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                }));
      }

      readyLatch.await(5, TimeUnit.SECONDS);
      startLatch.countDown();

      for (Future<?> f : futures) {
        f.get(5, TimeUnit.SECONDS);
      }

      assertTrue(instance.isTerminal());
      assertTrue(instance.isCleanedUp());

      // Verify every hook ran exactly once
      for (int i = 0; i < registerThreads; i++) {
        assertEquals(
            1,
            hookCounters[i].get(),
            "Hook " + i + " must execute exactly once (never dropped, never duplicated)");
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @RepeatedTest(20)
  @DisplayName(
      "Racing cancellable registration vs cleanup execution cancels each task exactly once")
  void racingCancellableRegistrationVsCleanup() throws Exception {
    int registerThreads = 32;
    int cleanupThreads = 8;
    int totalThreads = registerThreads + cleanupThreads;
    ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
    try {
      ExecutionPlanInstance instance = createInstance(UUID.randomUUID(), 1);
      AtomicInteger[] cancelCounters = new AtomicInteger[registerThreads];
      for (int i = 0; i < registerThreads; i++) {
        cancelCounters[i] = new AtomicInteger(0);
      }

      CountDownLatch readyLatch = new CountDownLatch(totalThreads);
      CountDownLatch startLatch = new CountDownLatch(1);

      List<Future<?>> futures = new ArrayList<>();

      // Registration threads
      for (int i = 0; i < registerThreads; i++) {
        final int taskIndex = i;
        CancellableTask task = () -> cancelCounters[taskIndex].incrementAndGet();
        futures.add(
            executor.submit(
                () -> {
                  readyLatch.countDown();
                  try {
                    startLatch.await();
                    instance.registerCancellable(task);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                }));
      }

      // Cleanup threads
      for (int i = 0; i < cleanupThreads; i++) {
        futures.add(
            executor.submit(
                () -> {
                  readyLatch.countDown();
                  try {
                    startLatch.await();
                    instance.cancel();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                }));
      }

      readyLatch.await(5, TimeUnit.SECONDS);
      startLatch.countDown();

      for (Future<?> f : futures) {
        f.get(5, TimeUnit.SECONDS);
      }

      assertTrue(instance.isTerminal());
      assertTrue(instance.isCleanedUp());

      // Verify every task was cancelled exactly once
      for (int i = 0; i < registerThreads; i++) {
        assertEquals(
            1,
            cancelCounters[i].get(),
            "Task " + i + " must be cancelled exactly once (never missed, never duplicated)");
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName("Concurrent callbacks and verification under registry")
  void concurrentCallbacksAndVerification() throws Exception {
    int threadCount = 20;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    try {
      ExecutionRegistry registry = new ExecutionRegistry();
      List<UUID> players = new ArrayList<>();
      List<ExecutionPlanInstance> instances = new ArrayList<>();

      for (int i = 0; i < 10; i++) {
        UUID p = UUID.randomUUID();
        players.add(p);
        ExecutionPlanInstance inst = createInstance(p, 1);
        instances.add(inst);
        registry.register(inst);
      }

      CountDownLatch readyLatch = new CountDownLatch(threadCount);
      CountDownLatch startLatch = new CountDownLatch(1);
      AtomicInteger validCallbacks = new AtomicInteger(0);

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < threadCount; i++) {
        final int index = i % 10;
        final ExecutionPlanInstance target = instances.get(index);
        final UUID player = players.get(index);

        futures.add(
            executor.submit(
                () -> {
                  readyLatch.countDown();
                  try {
                    startLatch.await();
                    var opt = registry.verifyAndGet(target.getExecutionId(), player, 1);
                    if (opt.isPresent()) {
                      validCallbacks.incrementAndGet();
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                }));
      }

      readyLatch.await(5, TimeUnit.SECONDS);
      startLatch.countDown();

      for (Future<?> f : futures) {
        f.get(5, TimeUnit.SECONDS);
      }

      assertEquals(threadCount, validCallbacks.get());
    } finally {
      executor.shutdownNow();
    }
  }
}
