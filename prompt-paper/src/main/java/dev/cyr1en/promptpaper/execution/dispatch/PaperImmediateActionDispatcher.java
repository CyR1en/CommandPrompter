package dev.cyr1en.promptpaper.execution.dispatch;

import dev.cyr1en.promptpaper.custom.PlayerExecutor;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.util.Scheduler;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Paper/Folia-compliant implementation of {@link ImmediateActionDispatcher}. */
public class PaperImmediateActionDispatcher implements ImmediateActionDispatcher {

  private final Plugin plugin;
  private final Scheduler scheduler;

  public PaperImmediateActionDispatcher(Plugin plugin, Scheduler scheduler) {
    this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
  }

  @Override
  public void dispatch(ImmediateActionRequest request, ActionDispatchCallback callback) {
    if (callback == null) {
      return;
    }
    if (request == null) {
      callback.onComplete(
          DispatchOutcome.failure(DispatchErrorKind.INVALID_REQUEST, "request is null"));
      return;
    }

    Player player = request.player();
    if (player == null) {
      callback.onComplete(
          DispatchOutcome.failure(DispatchErrorKind.INVALID_REQUEST, "player is null"));
      return;
    }

    PlayerExecutor initiator =
        request.initiatorExecutor() != null
            ? request.initiatorExecutor()
            : PlayerExecutor.forPlayer(plugin, player);

    AtomicBoolean completed = new AtomicBoolean(false);
    ActionDispatchCallback safeCallback =
        outcome -> {
          if (completed.compareAndSet(false, true)) {
            callback.onComplete(outcome);
          }
        };

    String rawCommand = request.command();
    if (rawCommand == null || rawCommand.isBlank()) {
      initiator.execute(
          () ->
              safeCallback.onComplete(
                  DispatchOutcome.failure(DispatchErrorKind.INVALID_REQUEST, "command is blank")),
          () ->
              safeCallback.onComplete(
                  DispatchOutcome.failure(
                      DispatchErrorKind.SCHEDULER_RETIRED, "player scheduler retired")));
      return;
    }

    String commandToExecute = DispatchSanitizer.stripLeadingSlash(rawCommand);

    if (request.executeAs() == ExecuteAs.CONSOLE) {
      ActionProvenance provenance = request.provenance();
      if (provenance == null || !provenance.isConsoleAuthorized(player)) {
        initiator.execute(
            () ->
                safeCallback.onComplete(
                    DispatchOutcome.failure(
                        DispatchErrorKind.UNAUTHORIZED_CONSOLE, "unauthorized console action")),
            () ->
                safeCallback.onComplete(
                    DispatchOutcome.failure(
                        DispatchErrorKind.SCHEDULER_RETIRED, "player scheduler retired")));
        return;
      }

      dispatchConsole(initiator, commandToExecute, safeCallback);
    } else {
      dispatchPlayer(initiator, player, commandToExecute, safeCallback);
    }
  }

  private void dispatchPlayer(
      PlayerExecutor initiator, Player player, String command, ActionDispatchCallback callback) {
    initiator.execute(
        () -> {
          try {
            boolean ok = Bukkit.dispatchCommand(player, command);
            if (ok) {
              callback.onComplete(DispatchOutcome.success());
            } else {
              callback.onComplete(
                  DispatchOutcome.failure(
                      DispatchErrorKind.DISPATCH_RETURNED_FALSE, "dispatch returned false"));
            }
          } catch (Throwable t) {
            callback.onComplete(DispatchOutcome.failure(DispatchErrorKind.EXCEPTION_THROWN, t));
          }
        },
        () ->
            callback.onComplete(
                DispatchOutcome.failure(
                    DispatchErrorKind.SCHEDULER_RETIRED, "player scheduler retired")));
  }

  private void dispatchConsole(
      PlayerExecutor initiator, String command, ActionDispatchCallback callback) {
    try {
      scheduler.runSync(
          () -> {
            DispatchOutcome outcome;
            try {
              var consoleSender = Bukkit.getConsoleSender();
              boolean ok = Bukkit.dispatchCommand(consoleSender, command);
              if (ok) {
                outcome = DispatchOutcome.success();
              } else {
                outcome =
                    DispatchOutcome.failure(
                        DispatchErrorKind.DISPATCH_RETURNED_FALSE, "dispatch returned false");
              }
            } catch (Throwable t) {
              outcome = DispatchOutcome.failure(DispatchErrorKind.EXCEPTION_THROWN, t);
            }

            final DispatchOutcome finalOutcome = outcome;
            initiator.execute(
                () -> callback.onComplete(finalOutcome),
                () ->
                    callback.onComplete(
                        DispatchOutcome.failure(
                            DispatchErrorKind.SCHEDULER_RETIRED, "initiator scheduler retired")));
          });
    } catch (Throwable t) {
      initiator.execute(
          () -> callback.onComplete(DispatchOutcome.failure(DispatchErrorKind.EXCEPTION_THROWN, t)),
          () ->
              callback.onComplete(
                  DispatchOutcome.failure(
                      DispatchErrorKind.SCHEDULER_RETIRED, "initiator scheduler retired")));
    }
  }
}
