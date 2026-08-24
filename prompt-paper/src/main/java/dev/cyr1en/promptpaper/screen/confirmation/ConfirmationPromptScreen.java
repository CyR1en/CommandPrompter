package dev.cyr1en.promptpaper.screen.confirmation;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Coordinator screen for confirmation prompts implementing {@link InputScreen}.
 *
 * <p>Coordinates an ordered fallback chain of {@link ConfirmationView} instances
 * (e.g. Dialog -> GUI -> Chat), guarantees exactly-once {@link ScreenResult}
 * delivery, and invalidates callbacks before closing views programmatically.
 */
public class ConfirmationPromptScreen implements InputScreen {

    public static final String DEFAULT_CONFIRM_VALUE = "confirm";
    public static final String DEFAULT_DECLINE_VALUE = "cancel";

    private final List<ConfirmationView> fallbackChain;
    private final String confirmValue;
    private final String declineValue;
    private final boolean declineAsCancel;
    private final Runnable onOpen;
    private final boolean valueMode;

    private Consumer<ScreenResult> callback;
    private Consumer<Throwable> openFailureCallback;
    private ConfirmationView activeView;
    private ConfirmationOutcome lastOutcome;
    private int currentViewIndex;
    private boolean open;

    public ConfirmationPromptScreen(ConfirmationView primaryView) {
        this(List.of(Objects.requireNonNull(primaryView, "primaryView must not be null")),
                DEFAULT_CONFIRM_VALUE, DEFAULT_DECLINE_VALUE, true);
    }

    public ConfirmationPromptScreen(ConfirmationView primaryView, String confirmValue, String declineValue) {
        this(List.of(Objects.requireNonNull(primaryView, "primaryView must not be null")),
                confirmValue, declineValue, true);
    }

    public ConfirmationPromptScreen(ConfirmationView... views) {
        this(List.of(views), DEFAULT_CONFIRM_VALUE, DEFAULT_DECLINE_VALUE, true);
    }

    public ConfirmationPromptScreen(List<ConfirmationView> fallbackChain) {
        this(fallbackChain, DEFAULT_CONFIRM_VALUE, DEFAULT_DECLINE_VALUE, true);
    }

    public ConfirmationPromptScreen(List<ConfirmationView> fallbackChain, String confirmValue, String declineValue) {
        this(fallbackChain, confirmValue, declineValue, true);
    }

    public ConfirmationPromptScreen(
            List<ConfirmationView> fallbackChain,
            String confirmValue,
            String declineValue,
            boolean declineAsCancel) {
        this(fallbackChain, confirmValue, declineValue, declineAsCancel, null, !declineAsCancel);
    }

    public ConfirmationPromptScreen(
            List<ConfirmationView> fallbackChain,
            String confirmValue,
            String declineValue,
            boolean declineAsCancel,
            Runnable onOpen,
            boolean valueMode) {
        Objects.requireNonNull(fallbackChain, "fallbackChain must not be null");
        if (fallbackChain.isEmpty()) {
            throw new IllegalArgumentException("fallbackChain must not be empty");
        }
        for (var view : fallbackChain) {
            Objects.requireNonNull(view, "view in fallbackChain must not be null");
        }
        this.fallbackChain = new ArrayList<>(fallbackChain);
        this.confirmValue = confirmValue != null ? confirmValue : DEFAULT_CONFIRM_VALUE;
        this.declineValue = declineValue != null ? declineValue : DEFAULT_DECLINE_VALUE;
        this.declineAsCancel = declineAsCancel;
        this.onOpen = onOpen;
        this.valueMode = valueMode;
    }

    @Override
    public synchronized void open() {
        if (open) return;
        this.lastOutcome = null;
        this.currentViewIndex = 0;
        if (onOpen != null) {
            try {
                onOpen.run();
            } catch (Throwable ignored) {
            }
        }
        tryOpenCurrentView();
    }

    private void tryOpenCurrentView() {
        if (fallbackChain.isEmpty() || currentViewIndex >= fallbackChain.size()) {
            open = false;
            activeView = null;
            if (openFailureCallback != null) {
                openFailureCallback.accept(
                        new IllegalStateException("All confirmation views in fallback chain failed to open"));
            }
            return;
        }

        var view = fallbackChain.get(currentViewIndex);
        this.activeView = view;
        this.open = true;

        view.onOpenFailure(error -> {
            synchronized (this) {
                if (!open || activeView != view) return;
                advanceFallback(error);
            }
        });

        try {
            view.open(this::handleOutcome);
        } catch (Throwable t) {
            synchronized (this) {
                if (!open || activeView != view) return;
                advanceFallback(t);
            }
        }
    }

    private void advanceFallback(Throwable reason) {
        var failedView = this.activeView;
        if (failedView != null && failedView.isOpen()) {
            try {
                failedView.close();
            } catch (Throwable ignored) {
            }
        }
        currentViewIndex++;
        if (currentViewIndex < fallbackChain.size()) {
            tryOpenCurrentView();
        } else {
            open = false;
            activeView = null;
            if (openFailureCallback != null) {
                openFailureCallback.accept(reason);
            }
        }
    }

    @Override
    public synchronized void close() {
        if (!open) return;
        this.open = false;
        this.callback = null;
        this.openFailureCallback = null;
        var viewToClose = this.activeView;
        this.activeView = null;
        if (viewToClose != null && viewToClose.isOpen()) {
            viewToClose.close();
        }
    }

    @Override
    public synchronized boolean isOpen() {
        return open;
    }

    @Override
    public synchronized void onResult(Consumer<ScreenResult> callback) {
        this.callback = callback;
    }

    @Override
    public synchronized void onOpenFailure(Consumer<Throwable> callback) {
        this.openFailureCallback = callback;
    }

    public synchronized void handleOutcome(ConfirmationOutcome outcome) {
        if (!open) {
            return;
        }
        this.open = false;
        this.lastOutcome = outcome;
        var resultCallback = this.callback;
        this.callback = null;

        var viewToClose = this.activeView;
        this.activeView = null;
        if (viewToClose != null && viewToClose.isOpen()) {
            viewToClose.close();
        }

        if (resultCallback != null) {
            resultCallback.accept(toScreenResult(outcome));
        }
    }

    public ScreenResult toScreenResult(ConfirmationOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        return switch (outcome) {
            case ConfirmationOutcome.Confirmed confirmed -> ScreenResult.answer(confirmValue);
            case ConfirmationOutcome.Declined declined ->
                    declineAsCancel
                            ? ScreenResult.cancel(CancelReason.GUI_EXIT)
                            : ScreenResult.answer(declineValue);
            case ConfirmationOutcome.Cancelled cancelled -> ScreenResult.cancel(cancelled.reason());
        };
    }

    // -- Accessors / metadata --

    public synchronized Optional<ConfirmationOutcome> lastOutcome() {
        return Optional.ofNullable(lastOutcome);
    }

    public synchronized Optional<ConfirmationView> activeView() {
        return Optional.ofNullable(activeView);
    }

    public List<ConfirmationView> fallbackChain() {
        return List.copyOf(fallbackChain);
    }

    public String confirmValue() {
        return confirmValue;
    }

    public String declineValue() {
        return declineValue;
    }

    public boolean isDeclinedAsCancel() {
        return declineAsCancel;
    }

    public boolean isValueMode() {
        return valueMode;
    }
}
