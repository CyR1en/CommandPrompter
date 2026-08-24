package dev.cyr1en.promptpaper.execution.dispatch;

import dev.cyr1en.promptpaper.custom.PlayerExecutor;
import dev.cyr1en.promptpaper.util.Scheduler;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

/**
 * Paper/Folia-compliant implementation of {@link PrimaryCommandDispatcher}.
 */
public class PaperPrimaryCommandDispatcher implements PrimaryCommandDispatcher {

    private final Plugin plugin;
    private final Scheduler scheduler;

    public PaperPrimaryCommandDispatcher(Plugin plugin, Scheduler scheduler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
    }

    @Override
    public void dispatch(PrimaryDispatchRequest request, PrimaryDispatchCallback callback) {
        if (callback == null) {
            return;
        }
        if (request == null) {
            callback.onComplete(DispatchOutcome.failure(DispatchErrorKind.INVALID_REQUEST, "request is null"));
            return;
        }

        Player player = request.player();
        if (player == null) {
            callback.onComplete(DispatchOutcome.failure(DispatchErrorKind.INVALID_REQUEST, "player is null"));
            return;
        }

        PlayerExecutor initiator = request.initiatorExecutor() != null
                ? request.initiatorExecutor()
                : PlayerExecutor.forPlayer(plugin, player);

        AtomicBoolean completed = new AtomicBoolean(false);
        PrimaryDispatchCallback safeCallback = outcome -> {
            if (completed.compareAndSet(false, true)) {
                callback.onComplete(outcome);
            }
        };

        String rawCommand = request.command();
        if (rawCommand == null || rawCommand.isBlank()) {
            initiator.execute(
                    () -> safeCallback.onComplete(DispatchOutcome.failure(
                            DispatchErrorKind.INVALID_REQUEST, "command is blank")),
                    () -> safeCallback.onComplete(DispatchOutcome.failure(
                            DispatchErrorKind.SCHEDULER_RETIRED, "player scheduler retired"))
            );
            return;
        }

        String commandToExecute = DispatchSanitizer.stripLeadingSlash(rawCommand);

        switch (request.mode()) {
            case PLAYER -> dispatchPlayer(initiator, player, commandToExecute, safeCallback);
            case CONSOLE -> dispatchConsole(commandToExecute, safeCallback);
            case ATTACHMENT -> dispatchAttachment(initiator, player, commandToExecute, request.attachmentContext(), safeCallback);
        }
    }

    private void dispatchPlayer(
            PlayerExecutor initiator,
            Player player,
            String command,
            PrimaryDispatchCallback callback
    ) {
        initiator.execute(
                () -> {
                    try {
                        boolean ok = Bukkit.dispatchCommand(player, command);
                        if (ok) {
                            callback.onComplete(DispatchOutcome.success());
                        } else {
                            callback.onComplete(DispatchOutcome.failure(
                                    DispatchErrorKind.DISPATCH_RETURNED_FALSE, "dispatch returned false"));
                        }
                    } catch (Throwable t) {
                        callback.onComplete(DispatchOutcome.failure(DispatchErrorKind.EXCEPTION_THROWN, t));
                    }
                },
                () -> callback.onComplete(DispatchOutcome.failure(
                        DispatchErrorKind.SCHEDULER_RETIRED, "player scheduler retired"))
        );
    }

    private void dispatchConsole(
            String command,
            PrimaryDispatchCallback callback
    ) {
        try {
            scheduler.runSync(() -> {
                DispatchOutcome outcome;
                try {
                    var consoleSender = Bukkit.getConsoleSender();
                    boolean ok = Bukkit.dispatchCommand(consoleSender, command);
                    if (ok) {
                        outcome = DispatchOutcome.success();
                    } else {
                        outcome = DispatchOutcome.failure(
                                DispatchErrorKind.DISPATCH_RETURNED_FALSE, "dispatch returned false");
                    }
                } catch (Throwable t) {
                    outcome = DispatchOutcome.failure(DispatchErrorKind.EXCEPTION_THROWN, t);
                }

                callback.onComplete(outcome);
            });
        } catch (Throwable t) {
            callback.onComplete(DispatchOutcome.failure(DispatchErrorKind.EXCEPTION_THROWN, t));
        }
    }

    private void dispatchAttachment(
            PlayerExecutor initiator,
            Player player,
            String command,
            PermissionAttachmentContext context,
            PrimaryDispatchCallback callback
    ) {
        if (context == null || !context.isValid()) {
            initiator.execute(
                    () -> callback.onComplete(DispatchOutcome.failure(
                            DispatchErrorKind.INVALID_ATTACHMENT, "invalid permission attachment context")),
                    () -> callback.onComplete(DispatchOutcome.failure(
                            DispatchErrorKind.SCHEDULER_RETIRED, "player scheduler retired"))
            );
            return;
        }

        initiator.execute(
                () -> {
                    PermissionAttachment attachment;
                    try {
                        attachment = player.addAttachment(plugin);
                    } catch (Throwable t) {
                        callback.onComplete(DispatchOutcome.failure(
                                DispatchErrorKind.ATTACHMENT_CREATION_FAILED, t));
                        return;
                    }

                    if (attachment == null) {
                        callback.onComplete(DispatchOutcome.failure(
                                DispatchErrorKind.ATTACHMENT_CREATION_FAILED, "unable to create permission attachment"));
                        return;
                    }

                    final PermissionAttachment finalAttachment = attachment;
                    AtomicBoolean removed = new AtomicBoolean(false);
                    Runnable removeTask = () -> {
                        if (removed.compareAndSet(false, true)) {
                            try {
                                player.removeAttachment(finalAttachment);
                            } catch (Throwable ignored) {
                            }
                        }
                    };

                    DispatchOutcome outcome;
                    try {
                        for (String perm : context.permissionSnapshot()) {
                            attachment.setPermission(perm, true);
                        }
                        attachment.getPermissible().recalculatePermissions();

                        boolean ok = Bukkit.dispatchCommand(player, command);
                        if (ok) {
                            outcome = DispatchOutcome.success();
                        } else {
                            outcome = DispatchOutcome.failure(
                                    DispatchErrorKind.DISPATCH_RETURNED_FALSE, "dispatch returned false");
                        }
                    } catch (Throwable t) {
                        outcome = DispatchOutcome.failure(DispatchErrorKind.EXCEPTION_THROWN, t);
                    }

                    // The attachment exists only for the synchronous dispatch call. Completion is
                    // never published while elevated permissions remain attached.
                    removeTask.run();
                    callback.onComplete(outcome);
                },
                () -> callback.onComplete(DispatchOutcome.failure(
                        DispatchErrorKind.SCHEDULER_RETIRED, "player scheduler retired"))
        );
    }
}
