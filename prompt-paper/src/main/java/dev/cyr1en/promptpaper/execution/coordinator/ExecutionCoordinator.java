package dev.cyr1en.promptpaper.execution.coordinator;

import dev.cyr1en.promptcore.SessionResult;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptcore.logic.transform.MathMode;
import dev.cyr1en.promptcore.logic.transform.RenderResult;
import dev.cyr1en.promptcore.logic.transform.TemplateBindings;
import dev.cyr1en.promptcore.plan.ExecutionPlanDefinition;
import dev.cyr1en.promptcore.plan.PreDispatchGateSpec;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.custom.PlayerExecutor;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.execution.dispatch.ActionProvenance;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchError;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchErrorKind;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchMode;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchOutcome;
import dev.cyr1en.promptpaper.execution.dispatch.ImmediateActionDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.ImmediateActionRequest;
import dev.cyr1en.promptpaper.execution.dispatch.PermissionAttachmentContext;
import dev.cyr1en.promptpaper.execution.dispatch.PrimaryCommandDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.PrimaryDispatchRequest;
import dev.cyr1en.promptpaper.execution.postaction.PostActionRunner;
import dev.cyr1en.promptpaper.execution.postaction.PostActionScheduler;
import dev.cyr1en.promptpaper.execution.postaction.template.PapiReferenceResolver;
import dev.cyr1en.promptpaper.execution.runtime.DispatchContextSnapshot;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionId;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionPlanInstance;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionRegistry;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionStage;
import dev.cyr1en.promptpaper.execution.runtime.InputCompletion;
import dev.cyr1en.promptpaper.hook.hooks.PapiHook;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.ExecutionPolicy;
import dev.cyr1en.promptpaper.preset.TrustedPresetAction;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/**
 * Coordinates the execution lifecycle of command prompt plans after input completion.
 *
 * <p>Phase 5.5 execution coordinator:
 * <ul>
 *   <li>Enforces single active execution per initiator via {@link ExecutionRegistry}.
 *   <li>Evaluates pre-dispatch approval gates sequentially in {@link ExecutionStage#PRE_DISPATCH_GATES}
 *       via {@link PreDispatchGateHandler}.
 *   <li>Upon gate approval, transitions to {@link ExecutionStage#PRIMARY_DISPATCH}, claims primary
 *       execution, and dispatches primary command.
 *   <li>Upon gate denial/timeout/disconnect, aborts primary command, dispatches optional immediate
 *       on-deny action, and runs lifecycle CANCEL post-actions.
 *   <li>Runs post-actions sequentially via {@link PostActionRunner} in {@link ExecutionStage#POST_ACTIONS},
 *       remaining active until runner completion before transitioning to terminal {@link ExecutionStage#COMPLETED}
 *       (or {@link ExecutionStage#ERROR} / {@link ExecutionStage#CANCELLED}).
 *   <li>Coordinates standalone input cancellation plans through {@link #coordinateCancellation(Player, InputCompletion)}.
 *   <li>All dispatcher and gate callbacks re-enter initiator {@link PlayerExecutor}, verify
 *       exact identity/stage/registry state.
 * </ul>
 */
public class ExecutionCoordinator {

  private static final Pattern C0_CONTROLS = Pattern.compile("[\\u0000-\\u001F\\u007F]");
  private static final int MAX_DETAIL_LENGTH = 256;
  private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

  private final CommandPrompter plugin;
  private final PromptEngine engine;
  private final ExecutionRegistry registry;
  private final PrimaryCommandDispatcher primaryDispatcher;
  private final ImmediateActionDispatcher immediateActionDispatcher;
  private final PreDispatchGateHandler preDispatchGateHandler;
  private final PostActionScheduler postActionScheduler;
  private final Function<Player, PapiReferenceResolver> papiResolverFactory;
  private final Function<Player, PlayerExecutor> playerExecutorFactory;

  public ExecutionCoordinator(
      CommandPrompter plugin,
      PromptEngine engine,
      ExecutionRegistry registry,
      PrimaryCommandDispatcher primaryDispatcher,
      ImmediateActionDispatcher immediateActionDispatcher) {
    this(plugin, engine, registry, primaryDispatcher, immediateActionDispatcher, null, null, null, null);
  }

  public ExecutionCoordinator(
      CommandPrompter plugin,
      PromptEngine engine,
      ExecutionRegistry registry,
      PrimaryCommandDispatcher primaryDispatcher,
      ImmediateActionDispatcher immediateActionDispatcher,
      PreDispatchGateHandler preDispatchGateHandler) {
    this(plugin, engine, registry, primaryDispatcher, immediateActionDispatcher, preDispatchGateHandler, null, null, null);
  }

  public ExecutionCoordinator(
      CommandPrompter plugin,
      PromptEngine engine,
      ExecutionRegistry registry,
      PrimaryCommandDispatcher primaryDispatcher,
      ImmediateActionDispatcher immediateActionDispatcher,
      Function<Player, PlayerExecutor> playerExecutorFactory) {
    this(plugin, engine, registry, primaryDispatcher, immediateActionDispatcher, null, null, null, playerExecutorFactory);
  }

  public ExecutionCoordinator(
      CommandPrompter plugin,
      PromptEngine engine,
      ExecutionRegistry registry,
      PrimaryCommandDispatcher primaryDispatcher,
      ImmediateActionDispatcher immediateActionDispatcher,
      PreDispatchGateHandler preDispatchGateHandler,
      Function<Player, PlayerExecutor> playerExecutorFactory) {
    this(
        plugin,
        engine,
        registry,
        primaryDispatcher,
        immediateActionDispatcher,
        preDispatchGateHandler,
        null,
        null,
        playerExecutorFactory);
  }

  public ExecutionCoordinator(
      CommandPrompter plugin,
      PromptEngine engine,
      ExecutionRegistry registry,
      PrimaryCommandDispatcher primaryDispatcher,
      ImmediateActionDispatcher immediateActionDispatcher,
      PreDispatchGateHandler preDispatchGateHandler,
      PostActionScheduler postActionScheduler,
      Function<Player, PapiReferenceResolver> papiResolverFactory,
      Function<Player, PlayerExecutor> playerExecutorFactory) {
    this.plugin = plugin;
    this.engine = engine;
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
    this.primaryDispatcher =
        Objects.requireNonNull(primaryDispatcher, "primaryDispatcher must not be null");
    this.immediateActionDispatcher =
        Objects.requireNonNull(immediateActionDispatcher, "immediateActionDispatcher must not be null");
    this.preDispatchGateHandler = preDispatchGateHandler;
    this.postActionScheduler =
        postActionScheduler != null
            ? postActionScheduler
            : (plugin != null ? PostActionScheduler.forPlugin(plugin) : (p, t, r, d) -> () -> {});
    this.papiResolverFactory =
        papiResolverFactory != null
            ? papiResolverFactory
            : p -> defaultPapiResolver(plugin, p);
    this.playerExecutorFactory =
        playerExecutorFactory != null
            ? playerExecutorFactory
            : p -> PlayerExecutor.forPlayer(plugin, p);
  }

  /** Returns the underlying execution registry. */
  public ExecutionRegistry getRegistry() {
    return registry;
  }

  /** Returns the primary command dispatcher. */
  public PrimaryCommandDispatcher getPrimaryDispatcher() {
    return primaryDispatcher;
  }

  /** Returns the immediate action dispatcher (seam for Phase 5.4 denial actions and 5.5 post-actions). */
  public ImmediateActionDispatcher getImmediateActionDispatcher() {
    return immediateActionDispatcher;
  }

  /** Returns the pre-dispatch gate handler. */
  public PreDispatchGateHandler getPreDispatchGateHandler() {
    return preDispatchGateHandler;
  }

  /** Returns the post-action scheduler. */
  public PostActionScheduler getPostActionScheduler() {
    return postActionScheduler;
  }

  /** Returns the PAPI resolver factory. */
  public Function<Player, PapiReferenceResolver> getPapiResolverFactory() {
    return papiResolverFactory;
  }

  /**
   * Sanitizes detail messages by stripping C0 control characters, capping length,
   * and escaping MiniMessage tags so it remains literal text when logged or sent to players.
   *
   * @param raw the raw detail message
   * @return sanitized, bounded, escaped message
   */
  public static String sanitizeDetail(String raw) {
    if (raw == null || raw.isBlank()) {
      return "unknown error";
    }
    String cleaned = C0_CONTROLS.matcher(raw).replaceAll("").strip();
    if (cleaned.length() > MAX_DETAIL_LENGTH) {
      cleaned = cleaned.substring(0, MAX_DETAIL_LENGTH);
    }
    if (cleaned.isBlank()) {
      return "unknown error";
    }
    return MINI_MESSAGE.escapeTags(cleaned);
  }

  /**
   * Default PAPI reference resolver adapter using the plugin's HookContainer and PapiHook.
   *
   * @param plugin CommandPrompter plugin instance
   * @param player context player
   * @return a PapiReferenceResolver
   */
  public static PapiReferenceResolver defaultPapiResolver(CommandPrompter plugin, Player player) {
    if (plugin == null || plugin.getHookContainer() == null || player == null) {
      return PapiReferenceResolver.empty();
    }
    var papiHookOpt = plugin.getHookContainer().getHook(PapiHook.class);
    if (papiHookOpt.isEmpty()) {
      return PapiReferenceResolver.empty();
    }
    PapiHook papiHook = papiHookOpt.get();
    return token -> {
      if (token == null || token.isBlank()) {
        return Optional.empty();
      }
      try {
        String query = "%" + token + "%";
        String expanded = papiHook.setPlaceholder(player, query);
        if (expanded == null || expanded.equals(query)) {
          return Optional.empty();
        }
        return Optional.of(expanded);
      } catch (Throwable t) {
        return Optional.empty();
      }
    };
  }

  /**
   * Coordinates the execution of an input completion plan.
   *
   * @param player initiator player
   * @param completion immutable input completion snapshot
   * @return optional containing the active execution instance, or empty if rejected
   */
  public Optional<ExecutionPlanInstance> coordinate(
      Player player, InputCompletion completion) {
    return coordinate(player, completion, null);
  }

  /**
   * Coordinates the execution of an input completion plan with backward-compatible sessionResult.
   *
   * @param player initiator player
   * @param completion immutable input completion snapshot
   * @param sessionResult optional raw session result for backward compatibility
   * @return optional containing the active execution instance, or empty if rejected
   */
  public Optional<ExecutionPlanInstance> coordinate(
      Player player, InputCompletion completion, SessionResult sessionResult) {
    Objects.requireNonNull(player, "player must not be null");
    Objects.requireNonNull(completion, "completion must not be null");

    if (plugin != null && (!plugin.isPluginActive() || (plugin.getEngine() != null && plugin.getEngine().isReloadInProgress()))) {
      return Optional.empty();
    }

    List<PreDispatchGateSpec> gates =
        completion.getCompiledPlan().map(ExecutionPlanDefinition::preDispatchGates).orElse(List.of());

    ExecutionId executionId = ExecutionId.create();
    ExecutionStage initialStage =
        gates.isEmpty() ? ExecutionStage.PRIMARY_DISPATCH : ExecutionStage.PRE_DISPATCH_GATES;
    ExecutionPlanInstance instance =
        new ExecutionPlanInstance(executionId, completion, initialStage);

    ExecutionRegistry.RegistrationResult regResult = registry.register(instance);
    if (!(regResult instanceof ExecutionRegistry.RegistrationResult.Success)) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().warn(
            "Rejected execution registration for initiator " + player.getUniqueId()
                + " (result: " + regResult.getClass().getSimpleName() + ")");
      }
      instance.tryTransitionTo(ExecutionStage.ERROR);
      reportCommandFailure(
          player,
          executionId,
          player.getUniqueId(),
          "execution registration rejected: active execution already in progress");
      return Optional.empty();
    }

    PlayerExecutor initiatorExecutor;
    try {
      initiatorExecutor = playerExecutorFactory.apply(player);
    } catch (Throwable t) {
      handleSynchronousFailure(player, instance, executionId, player.getUniqueId(), t, "executor creation failure");
      return Optional.of(instance);
    }

    if (gates.isEmpty()) {
      if (!instance.claimPrimaryExecution()) {
        instance.tryTransitionTo(ExecutionStage.ERROR);
        registry.removeIfExact(player.getUniqueId(), executionId);
        return Optional.of(instance);
      }
      try {
        dispatchPrimaryOrPostActions(player, instance, initiatorExecutor);
      } catch (Throwable t) {
        handleSynchronousFailure(player, instance, executionId, player.getUniqueId(), t, "primary dispatch error");
      }
      return Optional.of(instance);
    }

    if (preDispatchGateHandler == null) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().err(
            "PreDispatchGateHandler is not configured [execId=" + executionId + ", initiator=" + player.getUniqueId() + "]");
      }
      instance.tryTransitionTo(ExecutionStage.ERROR);
      registry.removeIfExact(player.getUniqueId(), executionId);
      reportCommandFailure(
          player,
          executionId,
          player.getUniqueId(),
          "pre-dispatch gates handler not available");
      return Optional.of(instance);
    }

    try {
      evaluateGateSequence(player, instance, gates, 0, initiatorExecutor);
    } catch (Throwable t) {
      handleSynchronousFailure(player, instance, executionId, player.getUniqueId(), t, "gate evaluation error");
    }
    return Optional.of(instance);
  }

  /**
   * Coordinates the cancellation execution of an input completion snapshot (e.g. from breakIf,
   * keyword cancel, or GUI exit before primary dispatch exists).
   *
   * @param player initiator player
   * @param completion immutable input completion snapshot
   * @return optional containing the active execution instance, or empty if registration rejected
   */
  public Optional<ExecutionPlanInstance> coordinateCancellation(
      Player player, InputCompletion completion) {
    Objects.requireNonNull(player, "player must not be null");
    Objects.requireNonNull(completion, "completion must not be null");

    if (plugin != null && (!plugin.isPluginActive() || (plugin.getEngine() != null && plugin.getEngine().isReloadInProgress()))) {
      return Optional.empty();
    }

    ExecutionId executionId = ExecutionId.create();
    ExecutionPlanInstance instance =
        new ExecutionPlanInstance(executionId, completion, ExecutionStage.POST_ACTIONS);

    ExecutionRegistry.RegistrationResult regResult = registry.register(instance);
    if (!(regResult instanceof ExecutionRegistry.RegistrationResult.Success)) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().warn(
            "Rejected cancellation execution registration for initiator " + player.getUniqueId()
                + " (result: " + regResult.getClass().getSimpleName() + ")");
      }
      instance.tryTransitionTo(ExecutionStage.ERROR);
      return Optional.empty();
    }

    PlayerExecutor initiatorExecutor;
    try {
      initiatorExecutor = playerExecutorFactory.apply(player);
    } catch (Throwable t) {
      handleSynchronousFailure(player, instance, executionId, completion.initiatorUuid(), t, "executor creation failure");
      return Optional.of(instance);
    }

    UUID initiatorUuid = completion.initiatorUuid();

    try {
      runPostActions(
          player,
          instance,
          initiatorExecutor,
          ExecutionPolicy.ON_CANCEL,
          () -> {
            instance.tryTransitionTo(ExecutionStage.CANCELLED);
            registry.removeIfExact(initiatorUuid, executionId);
          },
          error -> {
            instance.tryTransitionTo(ExecutionStage.ERROR);
            registry.removeIfExact(initiatorUuid, executionId);
          });
    } catch (Throwable t) {
      handleSynchronousFailure(player, instance, executionId, initiatorUuid, t, "cancellation post-action failure");
    }

    return Optional.of(instance);
  }

  private void evaluateGateSequence(
      Player player,
      ExecutionPlanInstance instance,
      List<PreDispatchGateSpec> gates,
      int gateIndex,
      PlayerExecutor initiatorExecutor) {
    if (gateIndex >= gates.size()) {
      if (!instance.tryTransitionTo(ExecutionStage.PRIMARY_DISPATCH)) {
        return;
      }
      if (!instance.claimPrimaryExecution()) {
        instance.tryTransitionTo(ExecutionStage.ERROR);
        registry.removeIfExact(instance.getInitiatorUuid(), instance.getExecutionId());
        return;
      }
      try {
        dispatchPrimaryOrPostActions(player, instance, initiatorExecutor);
      } catch (Throwable t) {
        handleSynchronousFailure(player, instance, instance.getExecutionId(), instance.getInitiatorUuid(), t, "primary dispatch error");
      }
      return;
    }

    PreDispatchGateSpec gateSpec = gates.get(gateIndex);
    ExecutionId executionId = instance.getExecutionId();
    UUID initiatorUuid = instance.getInitiatorUuid();
    long expectedIncarnation = instance.getIncarnation();

    try {
      preDispatchGateHandler.evaluateGate(
          player,
          instance,
          gateSpec,
          gateIndex,
          result -> {
            try {
              initiatorExecutor.execute(
                  () -> {
                    try {
                      handleGateResult(
                          player,
                          instance,
                          executionId,
                          initiatorUuid,
                          expectedIncarnation,
                          gates,
                          gateIndex,
                          result,
                          initiatorExecutor);
                    } catch (Throwable t) {
                      handleSynchronousFailure(player, instance, executionId, initiatorUuid, t, "gate callback error");
                    }
                  },
                  () -> handleInitiatorRetired(instance, executionId, initiatorUuid));
            } catch (Throwable t) {
              handleInitiatorRetired(instance, executionId, initiatorUuid);
            }
          });
    } catch (Throwable t) {
      handleSynchronousFailure(player, instance, executionId, initiatorUuid, t, "gate evaluation invocation error");
    }
  }

  private void handleGateResult(
      Player player,
      ExecutionPlanInstance instance,
      ExecutionId executionId,
      UUID initiatorUuid,
      long expectedIncarnation,
      List<PreDispatchGateSpec> gates,
      int gateIndex,
      PreDispatchGateResult result,
      PlayerExecutor initiatorExecutor) {
    Optional<ExecutionPlanInstance> verifiedOpt =
        registry.verifyAndGet(executionId, initiatorUuid, expectedIncarnation);
    if (verifiedOpt.isEmpty() || verifiedOpt.get() != instance) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().debug(
            "Discarding stale or unverified gate callback [execId=" + executionId
                + ", initiator=" + initiatorUuid
                + ", inc=" + expectedIncarnation + "]");
      }
      return;
    }

    if (instance.getStage() != ExecutionStage.PRE_DISPATCH_GATES) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().debug(
            "Discarding gate callback [execId=" + executionId
                + ", initiator=" + initiatorUuid
                + "] in unexpected stage: " + instance.getStage());
      }
      return;
    }

    if (result == null || result.status() == PreDispatchGateResult.Status.ERROR) {
      instance.tryTransitionTo(ExecutionStage.ERROR);
      registry.removeIfExact(initiatorUuid, executionId);
      reportCommandFailure(
          player,
          executionId,
          initiatorUuid,
          result != null && result.detail() != null ? result.detail() : "gate evaluation error");
      return;
    }

    if (result.status() == PreDispatchGateResult.Status.APPROVED) {
      evaluateGateSequence(player, instance, gates, gateIndex + 1, initiatorExecutor);
      return;
    }

    if (result.status() == PreDispatchGateResult.Status.INITIATOR_DISCONNECTED) {
      instance.tryTransitionTo(ExecutionStage.CANCELLED);
      registry.removeIfExact(initiatorUuid, executionId);
      return;
    }

    // DENIED, TIMED_OUT, or TARGET_DISCONNECTED -> abort primary
    Optional<TrustedPresetAction> onDenyOpt = result.getOnDenyAction();
    PreDispatchGateSpec gateSpec =
        (gateIndex >= 0 && gateIndex < gates.size()) ? gates.get(gateIndex) : null;
    String gateId =
        (gateSpec instanceof PreDispatchGateSpec.Approval approval)
            ? approval.presetId()
            : "unknown";
    if (onDenyOpt.isPresent()) {
      try {
        dispatchOnDenyAction(
            player,
            instance,
            executionId,
            initiatorUuid,
            expectedIncarnation,
            gateId,
            onDenyOpt.get(),
            initiatorExecutor);
      } catch (Throwable t) {
        if (plugin != null && plugin.getPluginLogger() != null) {
          plugin.getPluginLogger().err(
              "Exception in dispatchOnDenyAction [execId=" + executionId + ", initiator=" + initiatorUuid + "]: "
                  + sanitizeDetail(t.getMessage()));
        }
        runGateCancellationPostActions(player, instance, initiatorExecutor);
      }
    } else {
      runGateCancellationPostActions(player, instance, initiatorExecutor);
    }
  }

  private void dispatchOnDenyAction(
      Player player,
      ExecutionPlanInstance instance,
      ExecutionId executionId,
      UUID initiatorUuid,
      long expectedIncarnation,
      String gateId,
      TrustedPresetAction onDenyAction,
      PlayerExecutor initiatorExecutor) {
    Optional<ExecutionPlanInstance> verifiedOpt =
        registry.verifyAndGet(executionId, initiatorUuid, expectedIncarnation);
    if (verifiedOpt.isEmpty() || verifiedOpt.get() != instance) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().debug(
            "Discarding stale or unverified on-deny dispatch [execId=" + executionId
                + ", initiator=" + initiatorUuid
                + ", inc=" + expectedIncarnation + "]");
      }
      return;
    }

    if (instance.getStage() != ExecutionStage.PRE_DISPATCH_GATES) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().debug(
            "Discarding on-deny dispatch [execId=" + executionId
                + ", initiator=" + initiatorUuid
                + "] in unexpected stage: " + instance.getStage());
      }
      return;
    }

    InputCompletion completion = instance.getInputCompletion();
    TemplateBindings bindings = createTemplateBindings(player, completion.answers());
    RenderResult renderResult = onDenyAction.command().render(bindings, MathMode.STRICT);

    if (renderResult.isFailure()) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().err(
            "Failed to render on-deny action command [execId=" + executionId
                + ", initiator=" + initiatorUuid + "]: "
                + sanitizeDetail(renderResult.error() != null ? renderResult.error().message() : "render error"));
      }
      runGateCancellationPostActions(player, instance, initiatorExecutor);
      return;
    }

    String renderedCommand = renderResult.renderedText();
    ExecuteAs executeAs = onDenyAction.executeAs();
    String sourceId = "approval-gate:" + (gateId != null ? gateId : "unknown");
    ActionProvenance provenance =
        ActionProvenance.trustedPreset(sourceId, executeAs == ExecuteAs.CONSOLE);

    ImmediateActionRequest req =
        new ImmediateActionRequest(
            player, renderedCommand, executeAs, provenance, initiatorExecutor, sourceId);

    try {
      immediateActionDispatcher.dispatch(
          req,
          actionOutcome -> {
            try {
              initiatorExecutor.execute(
                  () -> {
                    try {
                      handleOnDenyOutcome(
                          player,
                          instance,
                          executionId,
                          initiatorUuid,
                          expectedIncarnation,
                          actionOutcome,
                          initiatorExecutor);
                    } catch (Throwable t) {
                      handleSynchronousFailure(player, instance, executionId, initiatorUuid, t, "on-deny callback error");
                    }
                  },
                  () -> handleInitiatorRetired(instance, executionId, initiatorUuid));
            } catch (Throwable t) {
              handleInitiatorRetired(instance, executionId, initiatorUuid);
            }
          });
    } catch (Throwable t) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().err(
            "Exception dispatching on-deny action [execId=" + executionId
                + ", initiator=" + initiatorUuid + "]: "
                + sanitizeDetail(t.getMessage()));
      }
      runGateCancellationPostActions(player, instance, initiatorExecutor);
    }
  }

  private void handleOnDenyOutcome(
      Player player,
      ExecutionPlanInstance instance,
      ExecutionId executionId,
      UUID initiatorUuid,
      long expectedIncarnation,
      DispatchOutcome actionOutcome,
      PlayerExecutor initiatorExecutor) {
    Optional<ExecutionPlanInstance> verifiedOpt =
        registry.verifyAndGet(executionId, initiatorUuid, expectedIncarnation);
    if (verifiedOpt.isEmpty() || verifiedOpt.get() != instance) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().debug(
            "Discarding stale or unverified on-deny callback [execId=" + executionId
                + ", initiator=" + initiatorUuid
                + ", inc=" + expectedIncarnation + "]");
      }
      return;
    }

    if (instance.getStage() != ExecutionStage.PRE_DISPATCH_GATES) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().debug(
            "Discarding on-deny callback [execId=" + executionId
                + ", initiator=" + initiatorUuid
                + "] in unexpected stage: " + instance.getStage());
      }
      return;
    }

    if (actionOutcome != null && !actionOutcome.isSuccess()) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().warn(
            "On-deny action execution failed [execId=" + executionId
                + ", initiator=" + initiatorUuid + "]: "
                + sanitizeDetail(actionOutcome.error() != null ? actionOutcome.error().detail() : "failed"));
      }
    }

    runGateCancellationPostActions(player, instance, initiatorExecutor);
  }

  private void runGateCancellationPostActions(
      Player player,
      ExecutionPlanInstance instance,
      PlayerExecutor initiatorExecutor) {
    UUID initiatorUuid = instance.getInitiatorUuid();
    ExecutionId executionId = instance.getExecutionId();
    if (!instance.tryTransitionTo(ExecutionStage.POST_ACTIONS)) {
      instance.tryTransitionTo(ExecutionStage.CANCELLED);
      registry.removeIfExact(initiatorUuid, executionId);
      return;
    }

    runPostActions(
        player,
        instance,
        initiatorExecutor,
        ExecutionPolicy.ON_CANCEL,
        () -> {
          instance.tryTransitionTo(ExecutionStage.CANCELLED);
          registry.removeIfExact(initiatorUuid, executionId);
        },
        error -> {
          instance.tryTransitionTo(ExecutionStage.CANCELLED);
          registry.removeIfExact(initiatorUuid, executionId);
        });
  }

  private void dispatchPrimaryOrPostActions(
      Player player,
      ExecutionPlanInstance instance,
      PlayerExecutor initiatorExecutor) {
    InputCompletion completion = instance.getInputCompletion();
    String command = completion.assembledCommand();
    UUID initiatorUuid = completion.initiatorUuid();
    ExecutionId executionId = instance.getExecutionId();

    if (command == null || command.isBlank()) {
      if (!instance.tryTransitionTo(ExecutionStage.POST_ACTIONS)) {
        return;
      }
      runPostActions(
          player,
          instance,
          initiatorExecutor,
          ExecutionPolicy.ON_COMPLETE,
          () -> {
            instance.tryTransitionTo(ExecutionStage.COMPLETED);
            registry.removeIfExact(initiatorUuid, executionId);
          },
          error -> {
            instance.tryTransitionTo(ExecutionStage.ERROR);
            registry.removeIfExact(initiatorUuid, executionId);
            reportCommandFailure(
                player,
                executionId,
                initiatorUuid,
                error != null && error.detail() != null ? error.detail() : "post-action failed");
          });
      return;
    }

    DispatchMode mode = mapDispatchMode(completion.dispatchContext());
    PermissionAttachmentContext attachmentContext =
        mapAttachmentContext(completion.dispatchContext());

    PrimaryDispatchRequest request =
        new PrimaryDispatchRequest(
            player, command, mode, attachmentContext, initiatorExecutor, null);

    long expectedIncarnation = completion.incarnation();

    try {
      primaryDispatcher.dispatch(
          request,
          outcome -> {
            try {
              initiatorExecutor.execute(
                  () -> {
                    try {
                      handleDispatchOutcome(
                          player,
                          instance,
                          executionId,
                          initiatorUuid,
                          expectedIncarnation,
                          command,
                          outcome,
                          completion,
                          initiatorExecutor);
                    } catch (Throwable t) {
                      handleSynchronousFailure(player, instance, executionId, initiatorUuid, t, "primary dispatch callback error");
                    }
                  },
                  () -> handleInitiatorRetired(instance, executionId, initiatorUuid));
            } catch (Throwable t) {
              handleInitiatorRetired(instance, executionId, initiatorUuid);
            }
          });
    } catch (Throwable t) {
      handleSynchronousFailure(player, instance, executionId, initiatorUuid, t, "primary dispatcher error");
    }
  }

  private void runPostActions(
      Player player,
      ExecutionPlanInstance instance,
      PlayerExecutor initiatorExecutor,
      ExecutionPolicy lifecyclePolicy,
      Runnable onSuccess,
      Consumer<DispatchError> onFailure) {
    InputCompletion completion = instance.getInputCompletion();
    ExecutionId executionId = instance.getExecutionId();
    UUID initiatorUuid = completion.initiatorUuid();
    long expectedIncarnation = completion.incarnation();

    PapiReferenceResolver papiResolver;
    try {
      papiResolver =
          papiResolverFactory != null
              ? papiResolverFactory.apply(player)
              : defaultPapiResolver(plugin, player);
      if (papiResolver == null) {
        papiResolver = PapiReferenceResolver.empty();
      }
    } catch (Throwable t) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().err(
            "Exception resolving PapiReferenceResolver [execId=" + executionId
                + ", initiator=" + initiatorUuid + "]: "
                + sanitizeDetail(t.getMessage()));
      }
      onFailure.accept(
          DispatchError.of(
              DispatchErrorKind.EXCEPTION_THROWN,
              "Exception resolving PapiReferenceResolver: " + sanitizeDetail(t.getMessage()),
              t));
      return;
    }

    var templateSyntax = plugin != null && plugin.getConfigLoader() != null && plugin.getConfigLoader().getConfig() != null
            ? plugin.getConfigLoader().getConfig().templateSyntax()
            : dev.cyr1en.promptcore.logic.transform.TemplateSyntax.DEFAULT;

    try {
      PostActionRunner.execute(
          player,
          initiatorExecutor,
          instance,
          completion,
          lifecyclePolicy,
          immediateActionDispatcher,
          postActionScheduler,
          papiResolver,
          templateSyntax,
          result -> {
            try {
              initiatorExecutor.execute(
                  () -> {
                    try {
                      Optional<ExecutionPlanInstance> verifiedOpt =
                          registry.verifyAndGet(executionId, initiatorUuid, expectedIncarnation);
                      if (verifiedOpt.isEmpty() || verifiedOpt.get() != instance) {
                        return;
                      }
                      if (instance.getStage() != ExecutionStage.POST_ACTIONS) {
                        return;
                      }
                      if (result != null && result.isSuccess()) {
                        onSuccess.run();
                      } else {
                        onFailure.accept(result != null ? result.error() : null);
                      }
                    } catch (Throwable t) {
                      handleSynchronousFailure(player, instance, executionId, initiatorUuid, t, "post-action callback error");
                    }
                  },
                  () -> handleInitiatorRetired(instance, executionId, initiatorUuid));
            } catch (Throwable t) {
              handleInitiatorRetired(instance, executionId, initiatorUuid);
            }
          });
    } catch (Throwable t) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().err(
            "Exception launching PostActionRunner [execId=" + executionId
                + ", initiator=" + initiatorUuid + "]: "
                + sanitizeDetail(t.getMessage()));
      }
      onFailure.accept(
          DispatchError.of(
              DispatchErrorKind.EXCEPTION_THROWN,
              "Exception launching PostActionRunner: " + sanitizeDetail(t.getMessage()),
              t));
    }
  }

  private void handleSynchronousFailure(
      Player player,
      ExecutionPlanInstance instance,
      ExecutionId executionId,
      UUID initiatorUuid,
      Throwable t,
      String category) {
    String detail = t != null && t.getMessage() != null ? t.getMessage() : category;
    if (plugin != null && plugin.getPluginLogger() != null) {
      plugin.getPluginLogger().err(
          "Execution failure [execId=" + executionId
              + ", initiator=" + initiatorUuid + "]: "
              + sanitizeDetail(detail));
    }
    instance.tryTransitionTo(ExecutionStage.ERROR);
    registry.removeIfExact(initiatorUuid, executionId);
    reportCommandFailure(player, executionId, initiatorUuid, detail);
  }

  private void handleInitiatorRetired(
      ExecutionPlanInstance instance,
      ExecutionId executionId,
      UUID initiatorUuid) {
    if (plugin != null && plugin.getPluginLogger() != null) {
      plugin.getPluginLogger().debug(
          "Initiator PlayerExecutor retired [execId=" + executionId
              + ", initiator=" + initiatorUuid
              + "]; running safe retirement cleanup");
    }
    instance.tryTransitionTo(ExecutionStage.ERROR);
    registry.removeIfExact(initiatorUuid, executionId);
  }

  private void handleDispatchOutcome(
      Player player,
      ExecutionPlanInstance instance,
      ExecutionId executionId,
      UUID initiatorUuid,
      long expectedIncarnation,
      String command,
      DispatchOutcome outcome,
      InputCompletion completion,
      PlayerExecutor initiatorExecutor) {
    Optional<ExecutionPlanInstance> verifiedOpt =
        registry.verifyAndGet(executionId, initiatorUuid, expectedIncarnation);
    if (verifiedOpt.isEmpty() || verifiedOpt.get() != instance) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().debug(
            "Discarding stale or unverified dispatch callback [execId=" + executionId
                + ", initiator=" + initiatorUuid
                + ", inc=" + expectedIncarnation + "]");
      }
      return;
    }

    if (instance.getStage() != ExecutionStage.PRIMARY_DISPATCH) {
      if (plugin != null && plugin.getPluginLogger() != null) {
        plugin.getPluginLogger().debug(
            "Discarding callback [execId=" + executionId
                + ", initiator=" + initiatorUuid
                + "] in unexpected stage: " + instance.getStage());
      }
      return;
    }

    if (outcome != null && outcome.isSuccess()) {
      if (!instance.tryTransitionTo(ExecutionStage.POST_ACTIONS)) {
        return;
      }

      sendCompletedCommand(player, command);

      runPostActions(
          player,
          instance,
          initiatorExecutor,
          ExecutionPolicy.ON_COMPLETE,
          () -> {
            instance.tryTransitionTo(ExecutionStage.COMPLETED);
            registry.removeIfExact(initiatorUuid, executionId);
          },
          error -> {
            instance.tryTransitionTo(ExecutionStage.ERROR);
            registry.removeIfExact(initiatorUuid, executionId);
            reportCommandFailure(
                player,
                executionId,
                initiatorUuid,
                error != null && error.detail() != null ? error.detail() : "post-action failed");
          });
    } else {
      String detail =
          outcome != null && outcome.error() != null
              ? outcome.error().detail()
              : "dispatch failed";
      reportCommandFailure(player, executionId, initiatorUuid, detail);

      if (!instance.tryTransitionTo(ExecutionStage.POST_ACTIONS)) {
        instance.tryTransitionTo(ExecutionStage.ERROR);
        registry.removeIfExact(initiatorUuid, executionId);
        return;
      }

      runPostActions(
          player,
          instance,
          initiatorExecutor,
          ExecutionPolicy.ON_CANCEL,
          () -> {
            instance.tryTransitionTo(ExecutionStage.ERROR);
            registry.removeIfExact(initiatorUuid, executionId);
          },
          error -> {
            instance.tryTransitionTo(ExecutionStage.ERROR);
            registry.removeIfExact(initiatorUuid, executionId);
          });
    }
  }

  public static TemplateBindings createTemplateBindings(Player player, List<String> answers) {
    String playerName = player != null ? player.getName() : "";
    return key -> {
      if ("player".equalsIgnoreCase(key)) {
        return playerName;
      }
      try {
        int idx = Integer.parseInt(key);
        if (idx >= 0 && idx < answers.size()) {
          return answers.get(idx);
        }
      } catch (NumberFormatException ignored) {
      }
      return null;
    };
  }

  private DispatchMode mapDispatchMode(DispatchContextSnapshot context) {
    if (context == null) return DispatchMode.PLAYER;
    if (context.isConsoleDelegated()) return DispatchMode.CONSOLE;
    if (context.attachmentRequired()) return DispatchMode.ATTACHMENT;
    return DispatchMode.PLAYER;
  }

  private PermissionAttachmentContext mapAttachmentContext(DispatchContextSnapshot context) {
    if (context == null || !context.attachmentRequired()) {
      return PermissionAttachmentContext.empty();
    }
    return new PermissionAttachmentContext(context.permissionKey(), context.permissionSnapshot());
  }

  private void sendCompletedCommand(Player player, String command) {
    if (player != null && plugin != null && plugin.getConfigLoader() != null
        && plugin.getConfigLoader().getConfig() != null
        && plugin.getConfigLoader().getConfig().showCompleted()) {
      try {
        player.sendMessage(net.kyori.adventure.text.Component.text(command));
      } catch (Throwable ignored) {
      }
    }
  }

  private void reportCommandFailure(
      Player player,
      ExecutionId executionId,
      UUID initiatorUuid,
      String detail) {
    String safeDetail = sanitizeDetail(detail);
    String execIdStr = executionId != null ? executionId.toString() : "unknown";
    String uuidStr = initiatorUuid != null ? initiatorUuid.toString() : (player != null ? player.getUniqueId().toString() : "unknown");

    if (plugin != null && plugin.getPluginLogger() != null) {
      plugin.getPluginLogger().info(
          "Command execution failed [execId=" + execIdStr + ", initiator=" + uuidStr + "]: " + safeDetail);
    }
    if (player != null && plugin != null && plugin.getConfigLoader() != null && plugin.getConfigLoader().getI18n() != null) {
      try {
        var executor = playerExecutorFactory.apply(player);
        executor.execute(
            () -> {
              try {
                player.sendMessage(
                    plugin.getConfigLoader().getI18n().get(
                        "prompt.error.command_failed",
                        player,
                        Placeholder.of("message", safeDetail)));
              } catch (Throwable ignored) {
              }
            },
            () -> {});
      } catch (Throwable ignored) {
      }
    }
  }

  /**
   * Cancels and removes the active execution for the given initiator UUID idempotently.
   *
   * @param initiator initiator UUID
   * @return true if found and cancelled
   */
  public boolean cancel(UUID initiator) {
    return registry.cancelAndRemove(initiator);
  }

  /**
   * Cancels and removes the execution corresponding to the given ExecutionId idempotently.
   *
   * @param executionId execution ID
   * @return true if found and cancelled
   */
  public boolean cancel(ExecutionId executionId) {
    return registry.cancelAndRemove(executionId);
  }

  /** Cancels all registered executions and clears the registry. */
  public void cancelAll() {
    registry.cancelAll();
  }
}
