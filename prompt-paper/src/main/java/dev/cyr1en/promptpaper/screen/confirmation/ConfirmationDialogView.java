package dev.cyr1en.promptpaper.screen.confirmation;

import dev.cyr1en.promptpaper.config.sub.DialogConfig;
import dev.cyr1en.promptui.ComponentUtil;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Native Paper Adventure Dialog implementation of {@link ConfirmationView}.
 *
 * <p>Presents a native modal confirmation dialog with heading/body, confirm action,
 * and decline action. All operations run on the player's entity scheduler.
 * Emits {@link ConfirmationOutcome.Confirmed} or {@link ConfirmationOutcome.Declined}
 * exactly once, and emits nothing on programmatic close.
 */
public class ConfirmationDialogView implements ConfirmationView {

    private static final Component DEFAULT_TITLE = Component.text("Confirmation");
    private static final Component DEFAULT_CONFIRM_LABEL = Component.text("Confirm");
    private static final Component DEFAULT_DECLINE_LABEL = Component.text("Cancel");

    private final Plugin plugin;
    private final Player player;
    private final Component title;
    private final Component body;
    private final Component confirmLabel;
    private final Component confirmTooltip;
    private final Component declineLabel;
    private final Component declineTooltip;
    private final DialogLauncher launcher;

    private Consumer<ConfirmationOutcome> callback;
    private Consumer<Throwable> failureCallback;

    private boolean open;
    private boolean programmaticClose;

    @FunctionalInterface
    public interface DialogLauncher {
        void show(Player player, Runnable onConfirm, Runnable onDecline) throws Exception;

        default void close(Player player) {
            try {
                player.closeDialog();
            } catch (Throwable ignored) {
            }
        }
    }

    public ConfirmationDialogView(Plugin plugin, Player player) {
        this(plugin, player, DEFAULT_TITLE, (Component) null);
    }

    public ConfirmationDialogView(Plugin plugin, Player player, Component title, Component body) {
        this(plugin, player, title, body, DEFAULT_CONFIRM_LABEL, DEFAULT_DECLINE_LABEL);
    }

    public ConfirmationDialogView(
            Plugin plugin,
            Player player,
            Component title,
            Component body,
            Component confirmLabel,
            Component declineLabel) {
        this(plugin, player, title, body, confirmLabel, null, declineLabel, null);
    }

    public ConfirmationDialogView(
            Plugin plugin,
            Player player,
            Component title,
            Component body,
            Component confirmLabel,
            Component confirmTooltip,
            Component declineLabel,
            Component declineTooltip) {
        this(
                plugin,
                player,
                title,
                body,
                confirmLabel,
                confirmTooltip,
                declineLabel,
                declineTooltip,
                null);
    }

    public ConfirmationDialogView(
            Plugin plugin,
            Player player,
            Component title,
            Component body,
            Component confirmLabel,
            Component confirmTooltip,
            Component declineLabel,
            Component declineTooltip,
            DialogLauncher launcher) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.player = Objects.requireNonNull(player, "player must not be null");
        this.title = title != null ? title : DEFAULT_TITLE;
        this.body = body;
        this.confirmLabel = confirmLabel != null ? confirmLabel : DEFAULT_CONFIRM_LABEL;
        this.confirmTooltip = confirmTooltip;
        this.declineLabel = declineLabel != null ? declineLabel : DEFAULT_DECLINE_LABEL;
        this.declineTooltip = declineTooltip;
        this.launcher = launcher;
    }

    public ConfirmationDialogView(
            Plugin plugin,
            Player player,
            String title,
            String body) {
        this(
                plugin,
                player,
                title != null ? ComponentUtil.mini(title) : DEFAULT_TITLE,
                body != null ? ComponentUtil.mini(body) : null);
    }

    public ConfirmationDialogView(
            Plugin plugin,
            Player player,
            String title,
            String body,
            String confirmLabel,
            String declineLabel) {
        this(
                plugin,
                player,
                title != null ? ComponentUtil.mini(title) : DEFAULT_TITLE,
                body != null ? ComponentUtil.mini(body) : null,
                confirmLabel != null ? ComponentUtil.mini(confirmLabel) : DEFAULT_CONFIRM_LABEL,
                declineLabel != null ? ComponentUtil.mini(declineLabel) : DEFAULT_DECLINE_LABEL);
    }

    public ConfirmationDialogView(
            Plugin plugin,
            Player player,
            String title,
            String body,
            String confirmLabel,
            String confirmTooltip,
            String declineLabel,
            String declineTooltip) {
        this(
                plugin,
                player,
                title != null ? ComponentUtil.mini(title) : DEFAULT_TITLE,
                body != null ? ComponentUtil.mini(body) : null,
                confirmLabel != null ? ComponentUtil.mini(confirmLabel) : DEFAULT_CONFIRM_LABEL,
                confirmTooltip != null ? ComponentUtil.mini(confirmTooltip) : null,
                declineLabel != null ? ComponentUtil.mini(declineLabel) : DEFAULT_DECLINE_LABEL,
                declineTooltip != null ? ComponentUtil.mini(declineTooltip) : null);
    }

    public ConfirmationDialogView(
            Plugin plugin,
            Player player,
            DialogConfig dialogConfig,
            Component body) {
        this(
                plugin,
                player,
                dialogConfig.title() != null ? ComponentUtil.mini(dialogConfig.title()) : DEFAULT_TITLE,
                body,
                dialogConfig.confirm() != null && dialogConfig.confirm().label() != null
                        ? ComponentUtil.mini(dialogConfig.confirm().label())
                        : DEFAULT_CONFIRM_LABEL,
                dialogConfig.confirm() != null && dialogConfig.confirm().tooltip() != null
                        ? ComponentUtil.mini(dialogConfig.confirm().tooltip())
                        : null,
                dialogConfig.cancel() != null && dialogConfig.cancel().label() != null
                        ? ComponentUtil.mini(dialogConfig.cancel().label())
                        : DEFAULT_DECLINE_LABEL,
                dialogConfig.cancel() != null && dialogConfig.cancel().tooltip() != null
                        ? ComponentUtil.mini(dialogConfig.cancel().tooltip())
                        : null);
    }

    public ConfirmationDialogView(
            Plugin plugin,
            Player player,
            DialogConfig dialogConfig,
            String body) {
        this(
                plugin,
                player,
                dialogConfig,
                body != null ? ComponentUtil.mini(body) : null);
    }

    private DialogLauncher resolveLauncher() {
        return launcher != null ? launcher : new NativeDialogLauncher();
    }

    @Override
    public synchronized void open(Consumer<ConfirmationOutcome> callback) {
        if (open) return;
        this.callback = Objects.requireNonNull(callback, "callback must not be null");
        this.programmaticClose = false;

        try {
            var activeLauncher = resolveLauncher();
            this.open = true;

            player.getScheduler().run(
                    plugin,
                    task -> {
                        synchronized (this) {
                            if (!open || programmaticClose) return;
                            try {
                                activeLauncher.show(player, this::handleConfirm, this::handleDecline);
                            } catch (Throwable t) {
                                handleOpenFailure(t);
                            }
                        }
                    },
                    () -> {
                        synchronized (this) {
                            if (open && !programmaticClose) {
                                handleOpenFailure(
                                        new IllegalStateException("Entity scheduler retired before dialog could be opened"));
                            }
                        }
                    });
        } catch (Throwable t) {
            handleOpenFailure(t);
        }
    }

    @Override
    public synchronized void close() {
        if (!open) return;
        this.open = false;
        this.programmaticClose = true;
        this.callback = null;

        closeClientDialog();
    }

    @Override
    public synchronized boolean isOpen() {
        return open;
    }

    @Override
    public synchronized void onOpenFailure(Consumer<Throwable> failureCallback) {
        this.failureCallback = failureCallback;
    }

    // -- Interaction handlers --

    private void handleConfirm() {
        try {
            player.getScheduler().run(
                    plugin,
                    task -> {
                        Consumer<ConfirmationOutcome> cb;
                        synchronized (this) {
                            if (!open || programmaticClose) return;
                            this.open = false;
                            this.programmaticClose = true;
                            cb = this.callback;
                            this.callback = null;
                        }

                        closeClientDialogDirect();

                        if (cb != null) {
                            cb.accept(ConfirmationOutcome.confirmed());
                        }
                    },
                    null);
        } catch (Throwable t) {
            Consumer<ConfirmationOutcome> cb;
            synchronized (this) {
                if (!open || programmaticClose) return;
                this.open = false;
                this.programmaticClose = true;
                cb = this.callback;
                this.callback = null;
            }
            if (cb != null) {
                cb.accept(ConfirmationOutcome.confirmed());
            }
        }
    }

    private void handleDecline() {
        try {
            player.getScheduler().run(
                    plugin,
                    task -> {
                        Consumer<ConfirmationOutcome> cb;
                        synchronized (this) {
                            if (!open || programmaticClose) return;
                            this.open = false;
                            this.programmaticClose = true;
                            cb = this.callback;
                            this.callback = null;
                        }

                        closeClientDialogDirect();

                        if (cb != null) {
                            cb.accept(ConfirmationOutcome.declined());
                        }
                    },
                    null);
        } catch (Throwable t) {
            Consumer<ConfirmationOutcome> cb;
            synchronized (this) {
                if (!open || programmaticClose) return;
                this.open = false;
                this.programmaticClose = true;
                cb = this.callback;
                this.callback = null;
            }
            if (cb != null) {
                cb.accept(ConfirmationOutcome.declined());
            }
        }
    }

    private void handleOpenFailure(Throwable t) {
        Consumer<Throwable> failureCb;
        synchronized (this) {
            this.open = false;
            this.callback = null;
            failureCb = this.failureCallback;
        }
        if (failureCb != null) {
            failureCb.accept(t);
        } else if (t instanceof RuntimeException re) {
            throw re;
        } else {
            throw new RuntimeException(t);
        }
    }

    private void closeClientDialog() {
        try {
            player.getScheduler().run(
                    plugin,
                    task -> closeClientDialogDirect(),
                    null);
        } catch (Throwable ignored) {
        }
    }

    private void closeClientDialogDirect() {
        try {
            if (launcher != null) {
                launcher.close(player);
            } else {
                player.closeDialog();
            }
        } catch (Throwable ignored) {
        }
    }

    // -- Native Dialog Launcher --

    private class NativeDialogLauncher implements DialogLauncher {

        @Override
        public void show(Player targetPlayer, Runnable onConfirm, Runnable onDecline) {
            var options = ClickCallback.Options.builder()
                    .uses(1)
                    .lifetime(Duration.ofMinutes(5))
                    .build();

            var confirmBtnBuilder = ActionButton.builder(confirmLabel)
                    .action(DialogAction.customClick((view, audience) -> onConfirm.run(), options));
            if (confirmTooltip != null && !confirmTooltip.equals(Component.empty())) {
                confirmBtnBuilder.tooltip(confirmTooltip);
            }
            var confirmBtn = confirmBtnBuilder.build();

            var declineBtnBuilder = ActionButton.builder(declineLabel)
                    .action(DialogAction.customClick((view, audience) -> onDecline.run(), options));
            if (declineTooltip != null && !declineTooltip.equals(Component.empty())) {
                declineBtnBuilder.tooltip(declineTooltip);
            }
            var declineBtn = declineBtnBuilder.build();

            var baseBuilder = DialogBase.builder(title)
                    .canCloseWithEscape(false)
                    .pause(false)
                    .afterAction(DialogAfterAction.NONE);

            if (body != null && !body.equals(Component.empty())) {
                baseBuilder.body(List.of(DialogBody.plainMessage(body)));
            }

            var dialog = Dialog.create(factory -> factory.empty()
                    .base(baseBuilder.build())
                    .type(io.papermc.paper.registry.data.dialog.type.DialogType.confirmation(confirmBtn, declineBtn)));

            targetPlayer.showDialog(dialog);
        }

        @Override
        public void close(Player targetPlayer) {
            try {
                targetPlayer.closeDialog();
            } catch (Throwable ignored) {
            }
        }
    }

    // -- Accessors for diagnostics and testing --

    public Plugin getPlugin() {
        return plugin;
    }

    public Player getPlayer() {
        return player;
    }

    public Component getTitle() {
        return title;
    }

    public Component getBody() {
        return body;
    }

    public Component getConfirmLabel() {
        return confirmLabel;
    }

    public Component getConfirmTooltip() {
        return confirmTooltip;
    }

    public Component getDeclineLabel() {
        return declineLabel;
    }

    public Component getDeclineTooltip() {
        return declineTooltip;
    }

    public DialogLauncher getLauncher() {
        return launcher;
    }
}
