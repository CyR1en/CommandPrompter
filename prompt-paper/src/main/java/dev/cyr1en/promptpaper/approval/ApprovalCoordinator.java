package dev.cyr1en.promptpaper.approval;

import dev.cyr1en.promptcore.logic.transform.MathMode;
import dev.cyr1en.promptcore.logic.transform.RenderResult;
import dev.cyr1en.promptcore.logic.transform.TemplateBindings;
import dev.cyr1en.promptcore.plan.ExecutionPlanDefinition;
import dev.cyr1en.promptcore.plan.PreDispatchGateSpec;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.custom.PlayerExecutor;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.execution.coordinator.ExecutionCoordinator;
import dev.cyr1en.promptpaper.execution.coordinator.PreDispatchGateCallback;
import dev.cyr1en.promptpaper.execution.coordinator.PreDispatchGateHandler;
import dev.cyr1en.promptpaper.execution.coordinator.PreDispatchGateResult;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchSanitizer;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionId;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionPlanInstance;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionRegistry;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionStage;
import dev.cyr1en.promptpaper.execution.runtime.InputCompletion;
import dev.cyr1en.promptpaper.preset.ApprovalGateDefinition;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import dev.cyr1en.promptpaper.preset.SelfApprovalPolicy;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.util.CancellableTask;
import dev.cyr1en.promptpaper.util.Scheduler;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Coordinates approval gate evaluation, capability issuance, interaction leases, presentation on
 * target player schedulers, and timeout/response handling.
 *
 * <p>Implements {@link PreDispatchGateHandler} to integrate seamlessly with {@link
 * ExecutionCoordinator}.
 */
public class ApprovalCoordinator implements PreDispatchGateHandler {

  @FunctionalInterface
  public interface TimeoutScheduler {
    CancellableTask schedule(
        Player initiatorPlayer, long delayTicks, Runnable onTimeout, Runnable onRetired);
  }

  public record BoundGate(
      int gateIndex,
      String gateId,
      ApprovalGateDefinition definition,
      UUID targetUuid,
      String targetName) {}

  public static final class BoundApprovalPlan {
    private final ExecutionId executionId;
    private final List<BoundGate> boundGates;
    private final Map<UUID, AtomicInteger> remainingGatesPerTarget;

    public BoundApprovalPlan(ExecutionId executionId, List<BoundGate> boundGates) {
      this.executionId = Objects.requireNonNull(executionId, "executionId must not be null");
      this.boundGates = List.copyOf(boundGates);
      this.remainingGatesPerTarget = new ConcurrentHashMap<>();
      for (BoundGate gate : boundGates) {
        remainingGatesPerTarget
            .computeIfAbsent(gate.targetUuid(), ignored -> new AtomicInteger())
            .incrementAndGet();
      }
    }

    public ExecutionId executionId() {
      return executionId;
    }

    public List<BoundGate> boundGates() {
      return boundGates;
    }

    public int getRemainingCount(UUID targetUuid) {
      if (targetUuid == null) return 0;
      var count = remainingGatesPerTarget.get(targetUuid);
      return count != null ? count.get() : 0;
    }

    public int decrementRemaining(UUID targetUuid) {
      if (targetUuid == null) return 0;
      var count = remainingGatesPerTarget.get(targetUuid);
      return count != null ? count.decrementAndGet() : 0;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof BoundApprovalPlan other)) return false;
      return Objects.equals(executionId, other.executionId)
          && Objects.equals(boundGates, other.boundGates);
    }

    @Override
    public int hashCode() {
      return Objects.hash(executionId, boundGates);
    }
  }

  private record CandidateGate(
      int gateIndex,
      String gateId,
      ApprovalGateDefinition definition,
      String targetStr,
      Player candidatePlayer) {}

  private record PendingApproval(
      ExecutionId executionId,
      ApprovalCapability capability,
      ApprovalGateDefinition gateDefinition,
      ExecutionPlanInstance instance,
      PreDispatchGateCallback callback,
      AtomicBoolean outcomeReported,
      AtomicReference<CancellableTask> timeoutTaskRef,
      BoundApprovalPlan boundPlan,
      int gateIndex) {}

  private final CommandPrompter plugin;
  private final ExecutionRegistry executionRegistry;
  private final ScreenManager screenManager;
  private final PromptEngine engine;
  private final ApprovalCapabilityRegistry capabilityRegistry;
  private final PlayerInteractionLeaseRegistry leaseRegistry;
  private final ChatApprovalPresenter presenter;
  private final Function<Player, PlayerExecutor> playerExecutorFactory;
  private final TimeoutScheduler timeoutScheduler;
  private final TargetResolver targetResolver;

  private final Map<ExecutionId, PendingApproval> pendingByExecution = new ConcurrentHashMap<>();
  private final Map<UUID, PendingApproval> pendingByTarget = new ConcurrentHashMap<>();
  private final Map<ExecutionId, BoundApprovalPlan> boundPlansByExecution =
      new ConcurrentHashMap<>();

  public ApprovalCoordinator(
      CommandPrompter plugin,
      Scheduler scheduler,
      ExecutionRegistry executionRegistry,
      ScreenManager screenManager,
      PromptEngine engine) {
    this(
        plugin,
        scheduler,
        executionRegistry,
        screenManager,
        engine,
        new ApprovalCapabilityRegistry(),
        new PlayerInteractionLeaseRegistry(),
        new ChatApprovalPresenter(),
        null,
        null,
        null);
  }

  public ApprovalCoordinator(
      CommandPrompter plugin,
      Scheduler scheduler,
      ExecutionRegistry executionRegistry,
      ScreenManager screenManager,
      PromptEngine engine,
      ApprovalCapabilityRegistry capabilityRegistry,
      PlayerInteractionLeaseRegistry leaseRegistry,
      ChatApprovalPresenter presenter,
      Function<Player, PlayerExecutor> playerExecutorFactory,
      TimeoutScheduler timeoutScheduler) {
    this(
        plugin,
        scheduler,
        executionRegistry,
        screenManager,
        engine,
        capabilityRegistry,
        leaseRegistry,
        presenter,
        playerExecutorFactory,
        timeoutScheduler,
        null);
  }

  public ApprovalCoordinator(
      CommandPrompter plugin,
      Scheduler scheduler,
      ExecutionRegistry executionRegistry,
      ScreenManager screenManager,
      PromptEngine engine,
      ApprovalCapabilityRegistry capabilityRegistry,
      PlayerInteractionLeaseRegistry leaseRegistry,
      ChatApprovalPresenter presenter,
      Function<Player, PlayerExecutor> playerExecutorFactory,
      TimeoutScheduler timeoutScheduler,
      TargetResolver targetResolver) {
    this.plugin = plugin;
    this.executionRegistry = executionRegistry;
    this.screenManager = screenManager;
    this.engine = engine;
    this.capabilityRegistry =
        capabilityRegistry != null ? capabilityRegistry : new ApprovalCapabilityRegistry();
    this.leaseRegistry =
        leaseRegistry != null ? leaseRegistry : new PlayerInteractionLeaseRegistry();
    this.presenter = presenter != null ? presenter : new ChatApprovalPresenter();
    this.playerExecutorFactory =
        playerExecutorFactory != null
            ? playerExecutorFactory
            : p -> PlayerExecutor.forPlayer(plugin, p);
    this.timeoutScheduler =
        timeoutScheduler != null
            ? timeoutScheduler
            : (p, delay, run, ret) -> defaultScheduleTimeout(p, delay, run, ret);
    this.targetResolver =
        targetResolver != null ? targetResolver : ApprovalCoordinator::defaultResolveTarget;
  }

  public ApprovalCapabilityRegistry getCapabilityRegistry() {
    return capabilityRegistry;
  }

  public PlayerInteractionLeaseRegistry getLeaseRegistry() {
    return leaseRegistry;
  }

  public ChatApprovalPresenter getPresenter() {
    return presenter;
  }

  /**
   * Returns the target resolver seam.
   *
   * <p>Target resolvers must return opaque candidate handles without reading player properties
   * prior to entering the candidate's {@link PlayerExecutor}.
   *
   * @return the target resolver
   */
  public TargetResolver getTargetResolver() {
    return targetResolver;
  }

  public Optional<BoundApprovalPlan> getBoundPlan(ExecutionId executionId) {
    if (executionId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(boundPlansByExecution.get(executionId));
  }

  public int boundPlansCount() {
    return boundPlansByExecution.size();
  }

  public synchronized boolean removeBoundPlanIfExact(
      ExecutionId executionId, BoundApprovalPlan plan) {
    if (executionId == null || plan == null) {
      return false;
    }
    return boundPlansByExecution.remove(executionId, plan);
  }

  @Override
  public void evaluateGate(
      Player initiatorPlayer,
      ExecutionPlanInstance instance,
      PreDispatchGateSpec gateSpec,
      int gateIndex,
      PreDispatchGateCallback callback) {
    Objects.requireNonNull(instance, "instance must not be null");
    Objects.requireNonNull(gateSpec, "gateSpec must not be null");
    Objects.requireNonNull(callback, "callback must not be null");

    ExecutionId executionId = instance.getExecutionId();
    InputCompletion completion = instance.getInputCompletion();
    PresetSnapshot snapshot = completion.capturedPresetSnapshot();
    long expectedIncarnation = instance.getIncarnation();
    UUID initiatorUuid = instance.getInitiatorUuid();

    BoundApprovalPlan plan = boundPlansByExecution.get(executionId);
    if (plan != null) {
      executeBoundGate(
          plan,
          gateIndex,
          instance,
          initiatorPlayer,
          initiatorUuid,
          expectedIncarnation,
          completion,
          callback);
      return;
    }

    List<PreDispatchGateSpec> allGateSpecs =
        completion
            .getCompiledPlan()
            .map(ExecutionPlanDefinition::preDispatchGates)
            .orElse(List.of());
    if (allGateSpecs.isEmpty()) {
      allGateSpecs = List.of(gateSpec);
    }

    TemplateBindings bindings =
        ExecutionCoordinator.createTemplateBindings(initiatorPlayer, completion.answers());

    List<CandidateGate> candidates = new ArrayList<>();
    for (int i = 0; i < allGateSpecs.size(); i++) {
      PreDispatchGateSpec spec = allGateSpecs.get(i);
      if (!(spec instanceof PreDispatchGateSpec.Approval approvalSpec)) {
        callback.onResult(PreDispatchGateResult.error("Unsupported gate spec: " + spec));
        return;
      }

      String gId = approvalSpec.presetId();
      Optional<ApprovalGateDefinition> gDefOpt = snapshot.getApprovalGate(gId);
      if (gDefOpt.isEmpty()) {
        callback.onResult(
            PreDispatchGateResult.error(
                "Approval gate '" + gId + "' not found in captured preset snapshot"));
        return;
      }

      ApprovalGateDefinition gDef = gDefOpt.get();
      RenderResult targetResult = gDef.target().render(bindings, MathMode.STRICT);
      if (targetResult.isFailure()
          || targetResult.renderedText() == null
          || targetResult.renderedText().isBlank()) {
        callback.onResult(
            PreDispatchGateResult.error(
                "Failed to render target approver template for gate '"
                    + gId
                    + "': "
                    + (targetResult.isFailure() ? targetResult.error() : "blank target")));
        return;
      }

      String targetStr = targetResult.renderedText().trim();
      Optional<Player> resolvedCandidateOpt = targetResolver.resolveTarget(targetStr);
      if (resolvedCandidateOpt.isEmpty()) {
        callback.onResult(
            PreDispatchGateResult.error(
                "Target approver '"
                    + targetStr
                    + "' for gate '"
                    + gId
                    + "' is offline or ambiguous"));
        return;
      }

      candidates.add(new CandidateGate(i, gId, gDef, targetStr, resolvedCandidateOpt.get()));
    }

    bindCandidateGatesChain(
        0,
        candidates,
        instance,
        initiatorPlayer,
        initiatorUuid,
        expectedIncarnation,
        completion,
        callback,
        gateIndex,
        new ArrayList<>(),
        new ArrayList<>());
  }

  private void bindCandidateGatesChain(
      int index,
      List<CandidateGate> candidates,
      ExecutionPlanInstance instance,
      Player initiatorPlayer,
      UUID initiatorUuid,
      long expectedIncarnation,
      InputCompletion completion,
      PreDispatchGateCallback callback,
      int gateIndexToExecute,
      List<BoundGate> boundGatesSoFar,
      List<UUID> provisionalClaimsSoFar) {

    ExecutionId executionId = instance.getExecutionId();

    if (instance.isTerminal()
        || instance.getStage() != ExecutionStage.PRE_DISPATCH_GATES
        || instance.getIncarnation() != expectedIncarnation) {
      rollbackProvisionalClaims(provisionalClaimsSoFar, executionId);
      return;
    }

    if (index >= candidates.size()) {
      BoundApprovalPlan boundPlan =
          new BoundApprovalPlan(executionId, List.copyOf(boundGatesSoFar));
      boundPlansByExecution.put(executionId, boundPlan);
      instance.registerCleanupHook(() -> cleanupExecution(executionId, boundPlan));

      executeBoundGate(
          boundPlan,
          gateIndexToExecute,
          instance,
          initiatorPlayer,
          initiatorUuid,
          expectedIncarnation,
          completion,
          callback);
      return;
    }

    CandidateGate candidate = candidates.get(index);
    Player candidatePlayer = candidate.candidatePlayer();
    PlayerExecutor candidateExecutor = playerExecutorFactory.apply(candidatePlayer);

    candidateExecutor.execute(
        () -> {
          if (instance.isTerminal()
              || instance.getStage() != ExecutionStage.PRE_DISPATCH_GATES
              || instance.getIncarnation() != expectedIncarnation) {
            rollbackProvisionalClaims(provisionalClaimsSoFar, executionId);
            return;
          }

          if (!candidatePlayer.isOnline()) {
            rollbackProvisionalClaims(provisionalClaimsSoFar, executionId);
            callback.onResult(
                PreDispatchGateResult.targetDisconnected(candidate.definition().onDenyAction()));
            return;
          }

          UUID targetUuid = candidatePlayer.getUniqueId();
          String targetName = candidatePlayer.getName();

          boolean matchesUuid = candidate.targetStr().equalsIgnoreCase(targetUuid.toString());
          boolean matchesExactName = candidatePlayer.getName().equals(candidate.targetStr());

          if (!matchesUuid && !matchesExactName) {
            rollbackProvisionalClaims(provisionalClaimsSoFar, executionId);
            callback.onResult(
                PreDispatchGateResult.error(
                    "Target approver '" + candidate.targetStr() + "' mismatch on owner thread"));
            return;
          }

          // Busy policy checks on target owner thread
          if (screenManager != null && screenManager.hasActiveScreen(candidatePlayer)) {
            rollbackProvisionalClaims(provisionalClaimsSoFar, executionId);
            callback.onResult(
                PreDispatchGateResult.error(
                    "Target approver " + targetName + " has an active screen"));
            return;
          }

          if (engine != null && engine.hasActiveSession(candidatePlayer)) {
            rollbackProvisionalClaims(provisionalClaimsSoFar, executionId);
            callback.onResult(
                PreDispatchGateResult.error(
                    "Target approver " + targetName + " has an active prompt session"));
            return;
          }

          if (executionRegistry != null) {
            Optional<ExecutionPlanInstance> active = executionRegistry.getByInitiator(targetUuid);
            if (active.isPresent() && !active.get().getExecutionId().equals(executionId)) {
              rollbackProvisionalClaims(provisionalClaimsSoFar, executionId);
              callback.onResult(
                  PreDispatchGateResult.error(
                      "Target approver " + targetName + " has an active command execution"));
              return;
            }
          }

          if (capabilityRegistry.isTargetBusy(targetUuid)) {
            rollbackProvisionalClaims(provisionalClaimsSoFar, executionId);
            callback.onResult(
                PreDispatchGateResult.error(
                    "Target approver " + targetName + " already has a pending approval request"));
            return;
          }

          Optional<PlayerInteractionLease> existingLease = leaseRegistry.getLease(targetUuid);
          if (existingLease.isPresent()) {
            PlayerInteractionLease l = existingLease.get();
            if (!l.isApproval() || !l.executionId().equals(executionId)) {
              rollbackProvisionalClaims(provisionalClaimsSoFar, executionId);
              callback.onResult(
                  PreDispatchGateResult.error(
                      "Target approver " + targetName + " has an active interaction lease"));
              return;
            }
          }

          // Establish provisional exact interaction claim
          Duration ttl = Duration.ofSeconds(Math.max(60, candidate.definition().timeout() * 2L));
          Optional<PlayerInteractionLease> leaseOpt =
              leaseRegistry.acquire(targetUuid, executionId, ttl);
          if (leaseOpt.isEmpty()) {
            rollbackProvisionalClaims(provisionalClaimsSoFar, executionId);
            callback.onResult(
                PreDispatchGateResult.error(
                    "Failed to acquire interaction lease for approver " + targetName));
            return;
          }

          provisionalClaimsSoFar.add(targetUuid);
          instance.registerCleanupHook(() -> leaseRegistry.releaseIfExact(targetUuid, executionId));
          boundGatesSoFar.add(
              new BoundGate(
                  candidate.gateIndex(),
                  candidate.gateId(),
                  candidate.definition(),
                  targetUuid,
                  targetName));

          bindCandidateGatesChain(
              index + 1,
              candidates,
              instance,
              initiatorPlayer,
              initiatorUuid,
              expectedIncarnation,
              completion,
              callback,
              gateIndexToExecute,
              boundGatesSoFar,
              provisionalClaimsSoFar);
        },
        () -> {
          rollbackProvisionalClaims(provisionalClaimsSoFar, executionId);
          callback.onResult(
              PreDispatchGateResult.targetDisconnected(candidate.definition().onDenyAction()));
        });
  }

  private void rollbackProvisionalClaims(List<UUID> provisionalClaims, ExecutionId executionId) {
    if (provisionalClaims != null && executionId != null) {
      for (UUID targetUuid : provisionalClaims) {
        leaseRegistry.releaseIfExact(targetUuid, executionId);
      }
    }
  }

  private void executeBoundGate(
      BoundApprovalPlan plan,
      int gateIndex,
      ExecutionPlanInstance instance,
      Player initiatorPlayer,
      UUID initiatorUuid,
      long expectedIncarnation,
      InputCompletion completion,
      PreDispatchGateCallback callback) {

    ExecutionId executionId = instance.getExecutionId();

    if (gateIndex >= plan.boundGates().size()) {
      callback.onResult(PreDispatchGateResult.error("Invalid gate index: " + gateIndex));
      return;
    }

    BoundGate currentGate = plan.boundGates().get(gateIndex);
    ApprovalGateDefinition gate = currentGate.definition();
    UUID targetUuid = currentGate.targetUuid();

    if (targetUuid.equals(initiatorUuid)) {
      if (gate.selfApprovalPolicy() == SelfApprovalPolicy.AUTO_APPROVE) {
        if (plugin != null && plugin.getPluginLogger() != null) {
          plugin
              .getPluginLogger()
              .info(
                  "Auto-approved gate '"
                      + gate.id()
                      + "' for self-execution by "
                      + (initiatorPlayer != null ? initiatorPlayer.getName() : initiatorUuid)
                      + " ("
                      + initiatorUuid
                      + ")");
        }
        int remaining = plan.decrementRemaining(targetUuid);
        if (remaining <= 0) {
          leaseRegistry.releaseIfExact(targetUuid, executionId);
        }
        if (gateIndex == plan.boundGates().size() - 1) {
          removeBoundPlanIfExact(executionId, plan);
        }
        callback.onResult(PreDispatchGateResult.approved());
        return;
      }
    }

    Optional<Player> targetOpt = targetResolver.resolveTarget(targetUuid.toString());
    if (targetOpt.isEmpty() && currentGate.targetName() != null) {
      targetOpt = targetResolver.resolveTarget(currentGate.targetName());
    }
    Player targetPlayer = targetOpt.orElseGet(() -> Bukkit.getPlayer(targetUuid));
    if (targetPlayer == null) {
      cleanupExecution(executionId, plan);
      if (targetUuid.equals(initiatorUuid)) {
        callback.onResult(PreDispatchGateResult.initiatorDisconnected());
      } else {
        callback.onResult(PreDispatchGateResult.targetDisconnected(gate.onDenyAction()));
      }
      return;
    }

    PlayerExecutor targetExecutor = playerExecutorFactory.apply(targetPlayer);

    targetExecutor.execute(
        () -> {
          if (instance.isTerminal()
              || instance.getStage() != ExecutionStage.PRE_DISPATCH_GATES
              || instance.getIncarnation() != expectedIncarnation) {
            return;
          }

          if (!targetPlayer.isOnline() || !targetPlayer.getUniqueId().equals(targetUuid)) {
            cleanupExecution(executionId, plan);
            if (targetUuid.equals(initiatorUuid)) {
              callback.onResult(PreDispatchGateResult.initiatorDisconnected());
            } else {
              callback.onResult(PreDispatchGateResult.targetDisconnected(gate.onDenyAction()));
            }
            return;
          }

          if (screenManager != null && screenManager.hasActiveScreen(targetPlayer)) {
            cleanupExecution(executionId, plan);
            callback.onResult(
                PreDispatchGateResult.error(
                    "Target approver " + targetPlayer.getName() + " has an active screen"));
            return;
          }

          if (engine != null && engine.hasActiveSession(targetPlayer)) {
            cleanupExecution(executionId, plan);
            callback.onResult(
                PreDispatchGateResult.error(
                    "Target approver " + targetPlayer.getName() + " has an active prompt session"));
            return;
          }

          if (executionRegistry != null) {
            Optional<ExecutionPlanInstance> active = executionRegistry.getByInitiator(targetUuid);
            if (active.isPresent() && !active.get().getExecutionId().equals(executionId)) {
              cleanupExecution(executionId, plan);
              callback.onResult(
                  PreDispatchGateResult.error(
                      "Target approver "
                          + targetPlayer.getName()
                          + " has an active command execution"));
              return;
            }
          }

          if (capabilityRegistry.isTargetBusy(targetUuid)) {
            cleanupExecution(executionId, plan);
            callback.onResult(
                PreDispatchGateResult.error(
                    "Target approver "
                        + targetPlayer.getName()
                        + " already has a pending approval request"));
            return;
          }

          TemplateBindings bindings =
              ExecutionCoordinator.createTemplateBindings(initiatorPlayer, completion.answers());
          RenderResult msgResult = gate.message().render(bindings, MathMode.STRICT);
          if (msgResult.isFailure()
              || msgResult.renderedText() == null
              || msgResult.renderedText().isBlank()) {
            cleanupExecution(executionId, plan);
            callback.onResult(
                PreDispatchGateResult.error(
                    "Failed to render approval message for gate '"
                        + gate.id()
                        + "': "
                        + (msgResult.isFailure() ? msgResult.error() : "blank message")));
            return;
          }
          String renderedMsg = msgResult.renderedText();

          Duration ttl = Duration.ofSeconds(gate.timeout());
          Optional<PlayerInteractionLease> leaseOpt =
              leaseRegistry.acquire(targetUuid, executionId, ttl);
          if (leaseOpt.isEmpty()) {
            cleanupExecution(executionId, plan);
            callback.onResult(
                PreDispatchGateResult.error(
                    "Failed to acquire interaction lease for approver " + targetPlayer.getName()));
            return;
          }

          Optional<ApprovalCapability> capOpt =
              capabilityRegistry.register(
                  executionId,
                  gate.id(),
                  initiatorUuid,
                  instance.getIncarnation(),
                  targetUuid,
                  ttl);
          if (capOpt.isEmpty()) {
            cleanupExecution(executionId, plan);
            callback.onResult(
                PreDispatchGateResult.error(
                    "Failed to register approval capability for approver "
                        + targetPlayer.getName()));
            return;
          }

          ApprovalCapability capability = capOpt.get();
          AtomicBoolean outcomeReported = new AtomicBoolean(false);
          AtomicReference<CancellableTask> timeoutRef = new AtomicReference<>();

          PendingApproval pending =
              new PendingApproval(
                  executionId,
                  capability,
                  gate,
                  instance,
                  callback,
                  outcomeReported,
                  timeoutRef,
                  plan,
                  gateIndex);

          pendingByExecution.put(executionId, pending);
          pendingByTarget.put(targetUuid, pending);

          instance.registerCleanupHook(() -> cleanupExecution(executionId, plan));

          long delayTicks = gate.timeout() * 20L;
          CancellableTask timeoutTask =
              timeoutScheduler.schedule(
                  initiatorPlayer,
                  delayTicks,
                  () -> {
                    if (outcomeReported.compareAndSet(false, true)) {
                      logTerminalDecision(
                          initiatorUuid, targetUuid, gate.id(), ApprovalDecision.TIMED_OUT.name());
                      cleanupExecution(executionId, plan);
                      callback.onResult(PreDispatchGateResult.timedOut(gate.onDenyAction()));
                    }
                  },
                  () -> {
                    if (outcomeReported.compareAndSet(false, true)) {
                      logTerminalDecision(
                          initiatorUuid,
                          targetUuid,
                          gate.id(),
                          ApprovalDecision.INITIATOR_DISCONNECTED.name());
                      cleanupExecution(executionId, plan);
                      callback.onResult(PreDispatchGateResult.initiatorDisconnected());
                    }
                  });

          timeoutRef.set(timeoutTask);
          instance.registerCancellable(timeoutTask);

          if (instance.isTerminal()
              || instance.getStage() != ExecutionStage.PRE_DISPATCH_GATES
              || instance.getIncarnation() != expectedIncarnation
              || outcomeReported.get()
              || pendingByExecution.get(executionId) != pending
              || !capabilityRegistry
                  .getByExecution(executionId)
                  .map(c -> c.equals(capability))
                  .orElse(false)
              || !leaseRegistry
                  .getLease(targetUuid)
                  .map(l -> executionId.equals(l.executionId()))
                  .orElse(false)
              || !capability.target().equals(targetUuid)
              || !capability.executionId().equals(executionId)
              || !capability.gateId().equals(gate.id())) {
            return;
          }

          presenter.present(targetPlayer, capability, renderedMsg);
        },
        () -> {
          cleanupExecution(executionId, plan);
          if (targetUuid.equals(initiatorUuid)) {
            callback.onResult(PreDispatchGateResult.initiatorDisconnected());
          } else {
            callback.onResult(PreDispatchGateResult.targetDisconnected(gate.onDenyAction()));
          }
        });
  }

  /**
   * Handles an incoming approval response from ResponseCommand as a single atomic transaction.
   *
   * @param responder the player issuing the response
   * @param nonce the raw nonce token
   * @param decisionStr the decision string (e.g. "confirm" / "decline")
   */
  public synchronized void handleResponse(Player responder, String nonce, String decisionStr) {
    Objects.requireNonNull(responder, "responder must not be null");
    if (nonce == null || decisionStr == null) {
      return;
    }
    UUID responderUuid = responder.getUniqueId();
    var rateLimiter = plugin != null ? plugin.getRateLimiter() : null;
    int attempts = rateLimiter != null ? rateLimiter.attemptCount(responderUuid) : 1;
    boolean shouldLog = rateLimiter == null || rateLimiter.shouldLogRejection(responderUuid);

    var parsedDecisionOpt = ApprovalDecision.parse(decisionStr);
    if (parsedDecisionOpt.isEmpty()) {
      if (plugin != null && plugin.getPluginLogger() != null && shouldLog) {
        plugin
            .getPluginLogger()
            .warn(
                "Approval response rejected: invalid decision for "
                    + responder.getName()
                    + " [nonce="
                    + ApprovalCapabilityRegistry.truncateNonce(nonce)
                    + ", attempts="
                    + attempts
                    + "]");
      }
      return;
    }

    ApprovalDecision decision = parsedDecisionOpt.get();
    if (decision != ApprovalDecision.APPROVED && decision != ApprovalDecision.DENIED) {
      if (plugin != null && plugin.getPluginLogger() != null && shouldLog) {
        plugin
            .getPluginLogger()
            .warn(
                "Approval response rejected: unsupported decision for "
                    + responder.getName()
                    + " [nonce="
                    + ApprovalCapabilityRegistry.truncateNonce(nonce)
                    + ", attempts="
                    + attempts
                    + "]");
      }
      return;
    }

    Optional<ApprovalCapability> consumedOpt = capabilityRegistry.consume(nonce, responderUuid);
    if (consumedOpt.isEmpty()) {
      if (plugin != null && plugin.getPluginLogger() != null && shouldLog) {
        plugin
            .getPluginLogger()
            .warn(
                "Approval response rejected (wrong target, replay, or expired) for "
                    + responder.getName()
                    + " [nonce="
                    + ApprovalCapabilityRegistry.truncateNonce(nonce)
                    + ", attempts="
                    + attempts
                    + "]");
      }
      return;
    }

    ApprovalCapability capability = consumedOpt.get();
    ApprovalOutcome outcome = ApprovalOutcome.of(capability, decision, Instant.now());
    handleOutcome(outcome);
  }

  /**
   * Handles an incoming approval outcome from ResponseCommand or test execution.
   *
   * @param outcome the immutable approval outcome
   */
  public synchronized void handleOutcome(ApprovalOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome must not be null");
    ApprovalCapability capability = outcome.capability();
    ExecutionId executionId = capability.executionId();

    PendingApproval pending = pendingByExecution.get(executionId);
    if (pending == null) {
      capabilityRegistry.invalidateNonce(capability.nonce());
      leaseRegistry.releaseIfExact(capability.target(), executionId);
      return;
    }

    if (!pending.capability().equals(capability)) {
      capabilityRegistry.invalidateNonce(capability.nonce());
      return;
    }

    pendingByExecution.remove(executionId, pending);
    pendingByTarget.remove(capability.target(), pending);

    if (pending.outcomeReported().compareAndSet(false, true)) {
      logTerminalDecision(
          pending.capability().initiator(),
          pending.capability().target(),
          pending.capability().gateId(),
          outcome.decision().name());
      if (pending.timeoutTaskRef().get() != null) {
        try {
          pending.timeoutTaskRef().get().cancel();
        } catch (Throwable ignored) {
        }
      }
      capabilityRegistry.invalidateExecution(executionId);

      ApprovalDecision decision = outcome.decision();
      switch (decision) {
        case APPROVED -> {
          int remaining =
              pending.boundPlan() != null
                  ? pending.boundPlan().decrementRemaining(capability.target())
                  : 0;
          if (remaining <= 0) {
            leaseRegistry.releaseIfExact(capability.target(), executionId);
          }
          if (pending.boundPlan() != null
              && pending.gateIndex() == pending.boundPlan().boundGates().size() - 1) {
            removeBoundPlanIfExact(executionId, pending.boundPlan());
          }
          pending.callback().onResult(PreDispatchGateResult.approved());
        }
        case DENIED -> {
          cleanupExecution(executionId, pending.boundPlan());
          pending
              .callback()
              .onResult(PreDispatchGateResult.denied(pending.gateDefinition().onDenyAction()));
        }
        case TIMED_OUT -> {
          cleanupExecution(executionId, pending.boundPlan());
          pending
              .callback()
              .onResult(PreDispatchGateResult.timedOut(pending.gateDefinition().onDenyAction()));
        }
        case TARGET_DISCONNECTED -> {
          cleanupExecution(executionId, pending.boundPlan());
          if (capability.initiator().equals(capability.target())) {
            pending.callback().onResult(PreDispatchGateResult.initiatorDisconnected());
          } else {
            pending
                .callback()
                .onResult(
                    PreDispatchGateResult.targetDisconnected(
                        pending.gateDefinition().onDenyAction()));
          }
        }
        case INITIATOR_DISCONNECTED -> {
          cleanupExecution(executionId, pending.boundPlan());
          pending.callback().onResult(PreDispatchGateResult.initiatorDisconnected());
        }
      }
    }
  }

  /**
   * Handles target player disconnection.
   *
   * @param targetUuid target player UUID
   */
  public synchronized void onTargetQuit(UUID targetUuid) {
    if (targetUuid == null) {
      return;
    }
    PendingApproval pending = pendingByTarget.get(targetUuid);
    if (pending == null) {
      return;
    }
    if (pending.capability().initiator().equals(targetUuid)
        || (pending.instance() != null
            && targetUuid.equals(pending.instance().getInitiatorUuid()))) {
      if (pending.outcomeReported().compareAndSet(false, true)) {
        logTerminalDecision(
            pending.capability().initiator(),
            pending.capability().target(),
            pending.capability().gateId(),
            ApprovalDecision.INITIATOR_DISCONNECTED.name());
        cleanupExecution(pending.executionId(), pending.boundPlan());
        pending.callback().onResult(PreDispatchGateResult.initiatorDisconnected());
      }
      return;
    }
    if (pending.outcomeReported().compareAndSet(false, true)) {
      logTerminalDecision(
          pending.capability().initiator(),
          pending.capability().target(),
          pending.capability().gateId(),
          ApprovalDecision.TARGET_DISCONNECTED.name());
      cleanupExecution(pending.executionId(), pending.boundPlan());
      pending
          .callback()
          .onResult(
              PreDispatchGateResult.targetDisconnected(pending.gateDefinition().onDenyAction()));
    }
  }

  /**
   * Handles initiator player disconnection.
   *
   * @param initiatorUuid initiator player UUID
   */
  public synchronized void onInitiatorQuit(UUID initiatorUuid) {
    if (initiatorUuid == null) {
      return;
    }
    List<ApprovalCapability> caps = capabilityRegistry.invalidateInitiator(initiatorUuid);
    for (ApprovalCapability cap : caps) {
      PendingApproval pending = pendingByExecution.get(cap.executionId());
      if (pending != null && pending.capability().equals(cap)) {
        if (pending.outcomeReported().compareAndSet(false, true)) {
          logTerminalDecision(
              pending.capability().initiator(),
              pending.capability().target(),
              pending.capability().gateId(),
              ApprovalDecision.INITIATOR_DISCONNECTED.name());
          cleanupExecution(cap.executionId(), pending.boundPlan());
          pending.callback().onResult(PreDispatchGateResult.initiatorDisconnected());
        }
      }
    }
  }

  private void logTerminalDecision(
      UUID initiatorUuid, UUID targetUuid, String gateId, String outcome) {
    if (plugin != null && plugin.getPluginLogger() != null) {
      String sanitizedGateId = DispatchSanitizer.sanitizeDetail(gateId);
      plugin
          .getPluginLogger()
          .info(
              "Approval gate '"
                  + sanitizedGateId
                  + "' decision: outcome="
                  + outcome
                  + " initiator="
                  + initiatorUuid
                  + " target="
                  + targetUuid);
    }
  }

  /**
   * Cleans up an execution exact-removing its bound plan and releasing all interaction claims and
   * capabilities.
   *
   * @param executionId execution ID
   * @param boundPlan bound plan if known, or null
   */
  public synchronized void cleanupExecution(ExecutionId executionId, BoundApprovalPlan boundPlan) {
    if (executionId == null) {
      return;
    }
    if (boundPlan != null) {
      removeBoundPlanIfExact(executionId, boundPlan);
    } else {
      boundPlansByExecution.remove(executionId);
    }
    PendingApproval pending = pendingByExecution.remove(executionId);
    if (pending != null) {
      pendingByTarget.remove(pending.capability().target(), pending);
      pending.outcomeReported().set(true);
      if (pending.timeoutTaskRef().get() != null) {
        try {
          pending.timeoutTaskRef().get().cancel();
        } catch (Throwable ignored) {
        }
      }
      if (pending.boundPlan() != null) {
        removeBoundPlanIfExact(executionId, pending.boundPlan());
      }
    }
    capabilityRegistry.invalidateExecution(executionId);
    leaseRegistry.releaseAllForExecution(executionId);
  }

  private CancellableTask defaultScheduleTimeout(
      Player player, long delayTicks, Runnable onTimeout, Runnable onRetired) {
    if (player == null) {
      if (onRetired != null) onRetired.run();
      return () -> {};
    }
    try {
      var scheduledTask =
          player.getScheduler().runDelayed(plugin, st -> onTimeout.run(), onRetired, delayTicks);
      if (scheduledTask == null) {
        if (onRetired != null) onRetired.run();
        return () -> {};
      }
      return () -> {
        try {
          scheduledTask.cancel();
        } catch (Throwable ignored) {
        }
      };
    } catch (Throwable t) {
      if (onRetired != null) onRetired.run();
      return () -> {};
    }
  }

  /**
   * Resolves a candidate target player handle by UUID or exact name lookup without reading player
   * properties.
   *
   * <p>This method performs platform lookup (via {@link Bukkit#getPlayer(UUID)} or {@link
   * Bukkit#getPlayerExact(String)}) solely to obtain an opaque candidate {@link Player} handle.
   * Target resolver implementations MUST NOT iterate over live players or call any methods on the
   * {@link Player} instance (such as {@code getName()}, {@code getUniqueId()}, or {@code
   * isOnline()}) prior to entering the candidate's {@link PlayerExecutor}. All exact-name matching,
   * online state checks, identity capture, and ambiguity validation are performed exclusively
   * inside the candidate player's {@link PlayerExecutor}.
   *
   * @param targetNameOrUuid the rendered target name or UUID string
   * @return optional containing the opaque candidate player handle, or empty if not found
   */
  public static Optional<Player> defaultResolveTarget(String targetNameOrUuid) {
    if (targetNameOrUuid == null || targetNameOrUuid.isBlank()) {
      return Optional.empty();
    }
    try {
      UUID uuid = UUID.fromString(targetNameOrUuid);
      return Optional.ofNullable(Bukkit.getPlayer(uuid));
    } catch (IllegalArgumentException ignored) {
    }
    return Optional.ofNullable(Bukkit.getPlayerExact(targetNameOrUuid));
  }

  /** Clears all state and invalidates all capabilities/leases. */
  public synchronized void clear() {
    capabilityRegistry.clear();
    leaseRegistry.clear();
    for (PendingApproval pending : pendingByExecution.values()) {
      if (pending.timeoutTaskRef().get() != null) {
        try {
          pending.timeoutTaskRef().get().cancel();
        } catch (Throwable ignored) {
        }
      }
    }
    pendingByExecution.clear();
    pendingByTarget.clear();
    boundPlansByExecution.clear();
  }

  /** Shuts down the approval coordinator. */
  public void shutdown() {
    clear();
  }
}
