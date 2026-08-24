package dev.cyr1en.promptpaper.execution.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import dev.cyr1en.promptcore.plan.ExecutionPlanDefinition;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecutionRegistryTest {

  private ExecutionRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new ExecutionRegistry();
  }

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
  @DisplayName("Registering an instance succeeds and can be retrieved by ID and initiator")
  void successfulRegistrationAndLookup() {
    UUID player = UUID.randomUUID();
    ExecutionPlanInstance instance = createInstance(player, 1);

    var result = registry.register(instance);
    assertInstanceOf(ExecutionRegistry.RegistrationResult.Success.class, result);

    assertEquals(1, registry.size());
    assertTrue(registry.hasActiveExecution(player));

    Optional<ExecutionPlanInstance> byId = registry.getByExecutionId(instance.getExecutionId());
    assertTrue(byId.isPresent());
    assertSame(instance, byId.get());

    Optional<ExecutionPlanInstance> byPlayer = registry.getByInitiator(player);
    assertTrue(byPlayer.isPresent());
    assertSame(instance, byPlayer.get());
  }

  @Test
  @DisplayName("Reject registration when initiator already has an active execution")
  void rejectSamePlayerCollision() {
    UUID player = UUID.randomUUID();
    ExecutionPlanInstance instance1 = createInstance(player, 1);
    ExecutionPlanInstance instance2 = createInstance(player, 2);

    var result1 = registry.register(instance1);
    assertInstanceOf(ExecutionRegistry.RegistrationResult.Success.class, result1);

    var result2 = registry.register(instance2);
    assertInstanceOf(ExecutionRegistry.RegistrationResult.RejectedAlreadyActive.class, result2);

    var rejected = (ExecutionRegistry.RegistrationResult.RejectedAlreadyActive) result2;
    assertSame(instance1, rejected.existingActiveInstance());
    assertEquals(1, registry.size());
  }

  @Test
  @DisplayName("Reject registration of an already-terminal instance")
  void rejectTerminalInstanceRegistration() {
    UUID player = UUID.randomUUID();
    ExecutionPlanInstance instance = createInstance(player, 1);
    instance.cancel();

    var result = registry.register(instance);
    assertInstanceOf(ExecutionRegistry.RegistrationResult.RejectedTerminal.class, result);
    assertEquals(0, registry.size());
  }

  @Test
  @DisplayName("Permit new registration when previous execution for initiator is terminal")
  void allowRegistrationAfterPreviousTerminal() {
    UUID player = UUID.randomUUID();
    ExecutionPlanInstance instance1 = createInstance(player, 1);
    registry.register(instance1);

    // Complete first execution
    instance1.transitionTo(ExecutionStage.PRIMARY_DISPATCH);
    instance1.transitionTo(ExecutionStage.COMPLETED);

    ExecutionPlanInstance instance2 = createInstance(player, 2);
    var result2 = registry.register(instance2);
    assertInstanceOf(ExecutionRegistry.RegistrationResult.Success.class, result2);

    assertSame(instance2, registry.getByInitiator(player).orElse(null));
  }

  @Test
  @DisplayName("verifyAndGet returns instance only when all caller criteria match")
  void verifyAndGetMatching() {
    UUID player = UUID.randomUUID();
    ExecutionPlanInstance instance = createInstance(player, 1);
    registry.register(instance);

    // Correct caller
    Optional<ExecutionPlanInstance> verified =
        registry.verifyAndGet(instance.getExecutionId(), player, 1);
    assertTrue(verified.isPresent());
    assertSame(instance, verified.get());

    // Wrong incarnation
    assertFalse(registry.verifyAndGet(instance.getExecutionId(), player, 2).isPresent());

    // Wrong player UUID
    assertFalse(
        registry.verifyAndGet(instance.getExecutionId(), UUID.randomUUID(), 1).isPresent());

    // Unknown execution ID
    assertFalse(registry.verifyAndGet(ExecutionId.create(), player, 1).isPresent());
  }

  @Test
  @DisplayName("verifyAndGet returns empty if instance transitioned to terminal stage")
  void verifyAndGetTerminalReturnsEmpty() {
    UUID player = UUID.randomUUID();
    ExecutionPlanInstance instance = createInstance(player, 1);
    registry.register(instance);

    instance.cancel();

    assertFalse(registry.verifyAndGet(instance.getExecutionId(), player, 1).isPresent());
  }

  @Test
  @DisplayName("removeIfExact removes matching execution and rejects stale execution ID")
  void staleIdRemoval() {
    UUID player = UUID.randomUUID();
    ExecutionPlanInstance currentInstance = createInstance(player, 2);
    registry.register(currentInstance);

    ExecutionId staleId = ExecutionId.create();

    // Attempt removal with stale ID for same player
    assertFalse(registry.removeIfExact(player, staleId));

    // Active instance is still preserved
    assertTrue(registry.hasActiveExecution(player));
    assertEquals(1, registry.size());

    // Exact ID removal succeeds
    assertTrue(registry.removeIfExact(player, currentInstance.getExecutionId()));
    assertFalse(registry.hasActiveExecution(player));
    assertEquals(0, registry.size());
  }

  @Test
  @DisplayName("cancelAndRemove idempotently cancels and removes instance")
  void cancelAndRemoveIdempotency() {
    UUID player = UUID.randomUUID();
    ExecutionPlanInstance instance = createInstance(player, 1);
    registry.register(instance);

    assertTrue(registry.cancelAndRemove(instance.getExecutionId()));
    assertTrue(instance.isTerminal());
    assertEquals(0, registry.size());

    // Second call returns false
    assertFalse(registry.cancelAndRemove(instance.getExecutionId()));
  }

  @Test
  @DisplayName("cancelAll cancels all executions and clears registry")
  void cancelAllClearsRegistry() {
    UUID p1 = UUID.randomUUID();
    UUID p2 = UUID.randomUUID();
    ExecutionPlanInstance inst1 = createInstance(p1, 1);
    ExecutionPlanInstance inst2 = createInstance(p2, 1);

    registry.register(inst1);
    registry.register(inst2);

    assertEquals(2, registry.size());

    registry.cancelAll();

    assertEquals(0, registry.size());
    assertTrue(inst1.isTerminal());
    assertTrue(inst2.isTerminal());
  }

  @Test
  @DisplayName("Instance terminal transition auto-cleans from registry")
  void autoCleanupOnTerminal() {
    UUID player = UUID.randomUUID();
    ExecutionPlanInstance instance = createInstance(player, 1);
    registry.register(instance);

    assertEquals(1, registry.size());

    instance.transitionTo(ExecutionStage.PRIMARY_DISPATCH);
    instance.transitionTo(ExecutionStage.COMPLETED);

    assertEquals(0, registry.size());
    assertFalse(registry.hasActiveExecution(player));
  }
}
