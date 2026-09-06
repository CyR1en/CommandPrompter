package dev.cyr1en.promptpaper.execution.postaction;

import dev.cyr1en.promptcore.PostCommandMeta;
import dev.cyr1en.promptcore.logic.transform.MathMode;
import dev.cyr1en.promptcore.logic.transform.TemplateSyntax;
import dev.cyr1en.promptcore.logic.transform.TransformNotice;
import dev.cyr1en.promptpaper.custom.PlayerExecutor;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchError;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchErrorKind;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchSanitizer;
import dev.cyr1en.promptpaper.execution.dispatch.ImmediateActionDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.ImmediateActionRequest;
import dev.cyr1en.promptpaper.execution.postaction.template.ActionTemplateBindings;
import dev.cyr1en.promptpaper.execution.postaction.template.ActionTemplateResult;
import dev.cyr1en.promptpaper.execution.postaction.template.PapiReferenceResolver;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionId;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionPlanInstance;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionStage;
import dev.cyr1en.promptpaper.execution.runtime.InputCompletion;
import dev.cyr1en.promptpaper.execution.runtime.NoticeFlag;
import dev.cyr1en.promptpaper.preset.ExecutionPolicy;
import dev.cyr1en.promptpaper.util.CancellableTask;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;

/**
 * Sequential runner for post-actions (immediate, delayed, conditional) attached to an {@link
 * ExecutionPlanInstance}.
 *
 * <p>Enforces:
 *
 * <ul>
 *   <li>Sequential execution in exact source order; next action begins only after prior delay +
 *       dispatch completion.
 *   <li>Strict caller and instance verification using immutable tokens captured at run start.
 *   <li>Enforces object identity between provided InputCompletion and instance-owned
 *       InputCompletion.
 *   <li>Owned cancellable handles registered with {@link ExecutionPlanInstance} and tracked for
 *       cleanup.
 *   <li>Thread-safe re-entry onto initiator {@link PlayerExecutor} from arbitrary dispatcher
 *       callback threads.
 *   <li>Synchronous dispatch/scheduler exception wrapping and exactly-once completion callback
 *       invocation.
 *   <li>Transform DIV0 notice recording through {@link NoticeFlag} without core logger
 *       dependencies.
 * </ul>
 */
public final class PostActionRunner {

  public static final int MAX_DELAY_TICKS = 72000;

  private final Player player;
  private final PlayerExecutor initiatorExecutor;
  private final ExecutionPlanInstance instance;
  private final InputCompletion inputCompletion;
  private final boolean completionMismatched;
  private final ExecutionPolicy lifecyclePolicy;
  private final ImmediateActionDispatcher dispatcher;
  private final PostActionScheduler scheduler;
  private final PapiReferenceResolver papiResolver;
  private final PostActionCallback callback;
  private final TemplateSyntax syntax;

  private final ExecutionId capturedExecutionId;
  private final UUID capturedInitiatorUuid;
  private final long capturedIncarnation;

  private final List<PostCommandMeta> postCommands;
  private final AtomicBoolean completed;
  private final AtomicReference<CancellableTask> activeDelayCancellable;
  private final AtomicReference<StepExecution> currentStep;

  private static final class StepExecution {
    final int index;
    final AtomicBoolean timerClaimed = new AtomicBoolean(false);
    final AtomicBoolean timerCancelled = new AtomicBoolean(false);
    final AtomicBoolean dispatchInvoked = new AtomicBoolean(false);
    final AtomicBoolean callbackConsumed = new AtomicBoolean(false);

    StepExecution(int index) {
      this.index = index;
    }
  }

  private PostActionRunner(
      Player player,
      PlayerExecutor initiatorExecutor,
      ExecutionPlanInstance instance,
      InputCompletion inputCompletion,
      ExecutionPolicy lifecyclePolicy,
      ImmediateActionDispatcher dispatcher,
      PostActionScheduler scheduler,
      PapiReferenceResolver papiResolver,
      TemplateSyntax syntax,
      PostActionCallback callback) {
    this.player = Objects.requireNonNull(player, "player must not be null");
    this.initiatorExecutor =
        Objects.requireNonNull(initiatorExecutor, "initiatorExecutor must not be null");
    this.instance = Objects.requireNonNull(instance, "instance must not be null");
    this.lifecyclePolicy =
        Objects.requireNonNull(lifecyclePolicy, "lifecyclePolicy must not be null");
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
    this.papiResolver = papiResolver != null ? papiResolver : PapiReferenceResolver.empty();
    this.syntax = syntax != null ? syntax : TemplateSyntax.DEFAULT;
    this.callback = Objects.requireNonNull(callback, "callback must not be null");

    this.inputCompletion = instance.getInputCompletion();
    this.completionMismatched =
        (inputCompletion != null && inputCompletion != this.inputCompletion);

    this.capturedExecutionId = instance.getExecutionId();
    this.capturedInitiatorUuid = player.getUniqueId();
    this.capturedIncarnation = this.inputCompletion.incarnation();

    this.postCommands = this.inputCompletion.postCommands();
    this.completed = new AtomicBoolean(false);
    this.activeDelayCancellable = new AtomicReference<>(null);
    this.currentStep = new AtomicReference<>(null);
  }

  /**
   * Executes the post-actions associated with the given plan instance and completion state using
   * default template syntax.
   */
  public static void execute(
      Player player,
      PlayerExecutor initiatorExecutor,
      ExecutionPlanInstance instance,
      InputCompletion inputCompletion,
      ExecutionPolicy lifecyclePolicy,
      ImmediateActionDispatcher dispatcher,
      PostActionScheduler scheduler,
      PapiReferenceResolver papiResolver,
      PostActionCallback callback) {
    execute(
        player,
        initiatorExecutor,
        instance,
        inputCompletion,
        lifecyclePolicy,
        dispatcher,
        scheduler,
        papiResolver,
        TemplateSyntax.DEFAULT,
        callback);
  }

  /**
   * Executes the post-actions associated with the given plan instance, completion state, and
   * template syntax. Enforces that {@code inputCompletion} must match {@code
   * instance.getInputCompletion()}.
   *
   * @param player initiator player
   * @param initiatorExecutor executor bound to initiator player's scheduler context
   * @param instance active execution plan instance (must be in {@link ExecutionStage#POST_ACTIONS})
   * @param inputCompletion captured input completion snapshot (must match instance)
   * @param lifecyclePolicy target lifecycle policy (ON_COMPLETE or ON_CANCEL)
   * @param dispatcher immediate action dispatcher
   * @param scheduler timer/scheduler seam for delayed actions
   * @param papiResolver placeholder resolver for PAPI references
   * @param syntax template syntax delimiters
   * @param callback completion callback invoked exactly once
   */
  public static void execute(
      Player player,
      PlayerExecutor initiatorExecutor,
      ExecutionPlanInstance instance,
      InputCompletion inputCompletion,
      ExecutionPolicy lifecyclePolicy,
      ImmediateActionDispatcher dispatcher,
      PostActionScheduler scheduler,
      PapiReferenceResolver papiResolver,
      TemplateSyntax syntax,
      PostActionCallback callback) {
    PostActionRunner runner =
        new PostActionRunner(
            player,
            initiatorExecutor,
            instance,
            inputCompletion,
            lifecyclePolicy,
            dispatcher,
            scheduler,
            papiResolver,
            syntax,
            callback);
    runner.start();
  }

  /** Convenience overload using the instance's own {@link InputCompletion}. */
  public static void execute(
      Player player,
      PlayerExecutor initiatorExecutor,
      ExecutionPlanInstance instance,
      ExecutionPolicy lifecyclePolicy,
      ImmediateActionDispatcher dispatcher,
      PostActionScheduler scheduler,
      PapiReferenceResolver papiResolver,
      PostActionCallback callback) {
    Objects.requireNonNull(instance, "instance must not be null");
    execute(
        player,
        initiatorExecutor,
        instance,
        instance.getInputCompletion(),
        lifecyclePolicy,
        dispatcher,
        scheduler,
        papiResolver,
        callback);
  }

  private void start() {
    if (completionMismatched) {
      instance.recordNotice(NoticeFlag.STALE_CALLER_DETECTED);
      notifyComplete(
          PostActionResult.failure(
              DispatchErrorKind.INVALID_REQUEST,
              "InputCompletion does not match instance-owned completion for execution "
                  + capturedExecutionId));
      return;
    }

    if (!instance.verify(capturedInitiatorUuid, capturedIncarnation, capturedExecutionId)) {
      instance.recordNotice(NoticeFlag.STALE_CALLER_DETECTED);
      notifyComplete(
          PostActionResult.failure(
              DispatchErrorKind.INVALID_REQUEST,
              "Caller or incarnation verification failed for execution " + capturedExecutionId));
      return;
    }

    if (instance.getStage() != ExecutionStage.POST_ACTIONS) {
      notifyComplete(
          PostActionResult.failure(
              DispatchErrorKind.INVALID_REQUEST,
              "ExecutionPlanInstance is not in POST_ACTIONS stage: " + instance.getStage()));
      return;
    }

    executeStep(0);
  }

  private void executeStep(int index) {
    if (completed.get() || instance.isTerminal()) {
      return;
    }

    if (index >= postCommands.size()) {
      notifyComplete(PostActionResult.success());
      return;
    }

    if (instance.getStage() != ExecutionStage.POST_ACTIONS) {
      notifyComplete(
          PostActionResult.failure(
              DispatchErrorKind.INVALID_REQUEST,
              "ExecutionPlanInstance is not in POST_ACTIONS stage: " + instance.getStage()));
      return;
    }

    StepExecution step = new StepExecution(index);
    currentStep.set(step);

    PostCommandMeta pcm = postCommands.get(index);
    ResolvedPostAction resolved;
    try {
      resolved =
          PostActionResolver.resolve(pcm, lifecyclePolicy, inputCompletion, papiResolver, syntax);
    } catch (PostActionResolutionException e) {
      instance.recordNotice(NoticeFlag.POST_ACTION_FAILED);
      notifyComplete(PostActionResult.failure(e.getError()));
      return;
    } catch (Throwable t) {
      instance.recordNotice(NoticeFlag.POST_ACTION_FAILED);
      notifyComplete(
          PostActionResult.failure(
              DispatchErrorKind.EXCEPTION_THROWN,
              "Exception resolving post-action: " + t.getMessage(),
              t));
      return;
    }

    if (resolved == null) {
      instance.recordNotice(NoticeFlag.POST_ACTION_SKIPPED);
      executeStep(index + 1);
      return;
    }

    if (resolved.isNoOp()) {
      executeStep(index + 1);
      return;
    }

    ActionTemplateResult renderResult;
    try {
      ActionTemplateBindings bindings =
          ActionTemplateBindings.of(inputCompletion.answers(), player.getName(), papiResolver);
      renderResult = resolved.template().render(bindings, MathMode.LEGACY);
    } catch (Throwable t) {
      instance.recordNotice(NoticeFlag.POST_ACTION_FAILED);
      notifyComplete(
          PostActionResult.failure(
              DispatchErrorKind.EXCEPTION_THROWN,
              "Exception rendering post-action template: " + t.getMessage(),
              t));
      return;
    }

    if (renderResult.isFailure()) {
      instance.recordNotice(NoticeFlag.POST_ACTION_FAILED);
      notifyComplete(PostActionResult.failure(renderResult.error()));
      return;
    }

    if (renderResult.notices().contains(TransformNotice.DIVISION_BY_ZERO_SUBSTITUTED)) {
      instance.recordNotice(NoticeFlag.CUSTOM_NOTICE);
    }

    String rawRendered = renderResult.renderedText();
    String commandToDispatch = DispatchSanitizer.stripLeadingSlash(rawRendered);

    int delayTicks = resolved.delayTicks();
    if (delayTicks < 0 || delayTicks > MAX_DELAY_TICKS) {
      instance.recordNotice(NoticeFlag.POST_ACTION_FAILED);
      notifyComplete(
          PostActionResult.failure(
              DispatchErrorKind.INVALID_REQUEST, "Delay ticks out of bounds: " + delayTicks));
      return;
    }

    if (delayTicks > 0) {
      scheduleDelayedAction(step, commandToDispatch, resolved, delayTicks);
    } else {
      dispatchAction(step, commandToDispatch, resolved);
    }
  }

  private void scheduleDelayedAction(
      StepExecution step, String commandToDispatch, ResolvedPostAction resolved, int delayTicks) {
    if (completed.get() || instance.isTerminal() || currentStep.get() != step) {
      return;
    }
    if (instance.getStage() != ExecutionStage.POST_ACTIONS) {
      notifyComplete(
          PostActionResult.failure(
              DispatchErrorKind.INVALID_REQUEST,
              "ExecutionPlanInstance is not in POST_ACTIONS stage: " + instance.getStage()));
      return;
    }

    AtomicReference<CancellableTask> underlyingHandle = new AtomicReference<>(null);

    CancellableTask cancellableWrapper =
        () -> {
          step.timerCancelled.set(true);
          CancellableTask raw = underlyingHandle.get();
          if (raw != null) {
            try {
              raw.cancel();
            } catch (Throwable ignored) {
            }
          }
        };

    activeDelayCancellable.set(cancellableWrapper);
    instance.registerCancellable(cancellableWrapper);

    if (completed.get()
        || instance.isTerminal()
        || step.timerCancelled.get()
        || currentStep.get() != step) {
      cancellableWrapper.cancel();
      return;
    }

    Runnable timerTask =
        () -> {
          try {
            if (!step.timerClaimed.compareAndSet(false, true)) {
              return;
            }
            if (step.timerCancelled.get()
                || completed.get()
                || instance.isTerminal()
                || currentStep.get() != step) {
              return;
            }
            if (!instance.verify(capturedInitiatorUuid, capturedIncarnation, capturedExecutionId)) {
              instance.recordNotice(NoticeFlag.STALE_CALLER_DETECTED);
              notifyComplete(
                  PostActionResult.failure(
                      DispatchErrorKind.INVALID_REQUEST,
                      "Caller or incarnation verification failed for execution "
                          + capturedExecutionId));
              return;
            }
            if (instance.getStage() != ExecutionStage.POST_ACTIONS) {
              notifyComplete(
                  PostActionResult.failure(
                      DispatchErrorKind.INVALID_REQUEST,
                      "ExecutionPlanInstance is not in POST_ACTIONS stage: "
                          + instance.getStage()));
              return;
            }
            dispatchAction(step, commandToDispatch, resolved);
          } catch (Throwable t) {
            instance.recordNotice(NoticeFlag.POST_ACTION_FAILED);
            notifyComplete(
                PostActionResult.failure(
                    DispatchErrorKind.EXCEPTION_THROWN,
                    "Exception executing delayed post-action timer task: " + t.getMessage(),
                    t));
          }
        };

    Runnable retiredCallback =
        () -> {
          if (!step.timerClaimed.compareAndSet(false, true)) {
            return;
          }
          if (step.timerCancelled.get()
              || completed.get()
              || instance.isTerminal()
              || currentStep.get() != step) {
            return;
          }
          cancelActiveDelay();
          instance.recordNotice(NoticeFlag.POST_ACTION_FAILED);
          notifyComplete(
              PostActionResult.failure(
                  DispatchErrorKind.SCHEDULER_RETIRED,
                  "Player entity scheduler retired during delayed post-action"));
        };

    try {
      CancellableTask rawTask =
          scheduler.scheduleDelayed(player, timerTask, retiredCallback, delayTicks);
      underlyingHandle.set(rawTask);
      if (step.timerCancelled.get()
          || completed.get()
          || instance.isTerminal()
          || currentStep.get() != step) {
        if (rawTask != null) {
          try {
            rawTask.cancel();
          } catch (Throwable ignored) {
          }
        }
      }
    } catch (Throwable t) {
      cancellableWrapper.cancel();
      instance.recordNotice(NoticeFlag.POST_ACTION_FAILED);
      notifyComplete(
          PostActionResult.failure(
              DispatchErrorKind.EXCEPTION_THROWN,
              "Failed to schedule delayed post-action: " + t.getMessage(),
              t));
    }
  }

  private void dispatchAction(StepExecution step, String command, ResolvedPostAction resolved) {
    if (!step.dispatchInvoked.compareAndSet(false, true)) {
      return;
    }
    if (completed.get() || instance.isTerminal() || currentStep.get() != step) {
      return;
    }
    if (instance.getStage() != ExecutionStage.POST_ACTIONS) {
      notifyComplete(
          PostActionResult.failure(
              DispatchErrorKind.INVALID_REQUEST,
              "ExecutionPlanInstance is not in POST_ACTIONS stage: " + instance.getStage()));
      return;
    }

    ImmediateActionRequest request =
        new ImmediateActionRequest(
            player,
            command,
            resolved.executeAs(),
            resolved.provenance(),
            initiatorExecutor,
            resolved.sourceId());

    try {
      dispatcher.dispatch(
          request,
          outcome -> {
            if (!step.callbackConsumed.compareAndSet(false, true)) {
              return;
            }
            if (completed.get() || instance.isTerminal() || currentStep.get() != step) {
              return;
            }
            try {
              // Dispatcher callbacks may be from arbitrary thread; always re-enter initiator
              // PlayerExecutor
              initiatorExecutor.execute(
                  () -> {
                    try {
                      if (completed.get() || instance.isTerminal() || currentStep.get() != step) {
                        return;
                      }
                      if (!instance.verify(
                          capturedInitiatorUuid, capturedIncarnation, capturedExecutionId)) {
                        instance.recordNotice(NoticeFlag.STALE_CALLER_DETECTED);
                        notifyComplete(
                            PostActionResult.failure(
                                DispatchErrorKind.INVALID_REQUEST,
                                "Caller or incarnation verification failed for execution "
                                    + capturedExecutionId));
                        return;
                      }
                      if (instance.getStage() != ExecutionStage.POST_ACTIONS) {
                        notifyComplete(
                            PostActionResult.failure(
                                DispatchErrorKind.INVALID_REQUEST,
                                "ExecutionPlanInstance is not in POST_ACTIONS stage: "
                                    + instance.getStage()));
                        return;
                      }
                      if (outcome != null && outcome.isSuccess()) {
                        executeStep(step.index + 1);
                      } else {
                        instance.recordNotice(NoticeFlag.POST_ACTION_FAILED);
                        DispatchError error =
                            outcome != null && outcome.error() != null
                                ? outcome.error()
                                : DispatchError.of(
                                    DispatchErrorKind.DISPATCH_RETURNED_FALSE,
                                    "Immediate action dispatch returned false");
                        notifyComplete(PostActionResult.failure(error));
                      }
                    } catch (Throwable t) {
                      instance.recordNotice(NoticeFlag.POST_ACTION_FAILED);
                      notifyComplete(
                          PostActionResult.failure(
                              DispatchErrorKind.EXCEPTION_THROWN,
                              "Exception during post-action dispatch completion: " + t.getMessage(),
                              t));
                    }
                  },
                  () -> {
                    cancelActiveDelay();
                    instance.recordNotice(NoticeFlag.POST_ACTION_FAILED);
                    notifyComplete(
                        PostActionResult.failure(
                            DispatchErrorKind.SCHEDULER_RETIRED,
                            "Player scheduler retired during action completion dispatch"));
                  });
            } catch (Throwable t) {
              cancelActiveDelay();
              instance.recordNotice(NoticeFlag.POST_ACTION_FAILED);
              notifyComplete(
                  PostActionResult.failure(
                      DispatchErrorKind.EXCEPTION_THROWN,
                      "Exception scheduling callback on PlayerExecutor: " + t.getMessage(),
                      t));
            }
          });
    } catch (Throwable t) {
      step.callbackConsumed.set(true);
      cancelActiveDelay();
      instance.recordNotice(NoticeFlag.POST_ACTION_FAILED);
      notifyComplete(
          PostActionResult.failure(
              DispatchErrorKind.EXCEPTION_THROWN,
              "ImmediateActionDispatcher threw exception during dispatch: " + t.getMessage(),
              t));
    }
  }

  private void cancelActiveDelay() {
    CancellableTask task = activeDelayCancellable.getAndSet(null);
    if (task != null) {
      try {
        task.cancel();
      } catch (Throwable ignored) {
      }
    }
  }

  private void notifyComplete(PostActionResult result) {
    if (completed.compareAndSet(false, true)) {
      cancelActiveDelay();
      try {
        callback.onComplete(result);
      } catch (Throwable ignored) {
      }
    }
  }
}
