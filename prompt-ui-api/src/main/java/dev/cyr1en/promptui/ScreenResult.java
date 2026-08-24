package dev.cyr1en.promptui;

import dev.cyr1en.promptcore.CancelReason;
import java.util.Objects;

/**
 * The result of an {@link InputScreen} interaction.
 *
 * <p>For successful input, use {@link #answer(String)}; for cancellation, use
 * {@link #cancel(CancelReason)} or {@link #cancel()}. Consumers can check
 * {@link #cancelled()} or inspect {@link #outcome()} and {@link #cancelReason()}.
 */
public record ScreenResult(String answer, Outcome outcome, CancelReason cancelReason) {

    /**
     * High-level interaction outcome category.
     */
    public enum Outcome {
        SUCCESS,
        CANCELLED
    }

    public ScreenResult {
        Objects.requireNonNull(answer, "answer");
        Objects.requireNonNull(outcome, "outcome");
        if (outcome == Outcome.CANCELLED && cancelReason == null) {
            cancelReason = CancelReason.GUI_EXIT;
        }
    }

    /**
     * Backward-compatible constructor accepting a boolean cancelled flag.
     *
     * @param answer the answer string
     * @param cancelled whether the interaction was cancelled
     */
    public ScreenResult(String answer, boolean cancelled) {
        this(
                answer,
                cancelled ? Outcome.CANCELLED : Outcome.SUCCESS,
                cancelled ? CancelReason.GUI_EXIT : null);
    }

    /**
     * Backward-compatible check for whether the interaction was cancelled.
     *
     * @return true if the interaction resulted in cancellation
     */
    public boolean cancelled() {
        return outcome == Outcome.CANCELLED;
    }

    /**
     * Creates a successful result with the given answer.
     *
     * @param answer the input answer string
     * @return a successful ScreenResult
     */
    public static ScreenResult answer(String answer) {
        return new ScreenResult(answer, Outcome.SUCCESS, null);
    }

    /**
     * Creates a cancelled result with the default {@link CancelReason#GUI_EXIT}.
     *
     * @return a cancelled ScreenResult
     */
    public static ScreenResult cancel() {
        return new ScreenResult("", Outcome.CANCELLED, CancelReason.GUI_EXIT);
    }

    /**
     * Creates a cancelled result with the specified {@link CancelReason}.
     *
     * @param reason the reason for cancellation
     * @return a cancelled ScreenResult
     */
    public static ScreenResult cancel(CancelReason reason) {
        return new ScreenResult(
                "",
                Outcome.CANCELLED,
                reason != null ? reason : CancelReason.GUI_EXIT);
    }

    /**
     * Creates a cancelled result with {@link CancelReason#MANUAL}.
     */
    public static ScreenResult manualCancel() {
        return cancel(CancelReason.MANUAL);
    }

    /**
     * Creates a cancelled result with {@link CancelReason#TIMEOUT}.
     */
    public static ScreenResult timeout() {
        return cancel(CancelReason.TIMEOUT);
    }

    /**
     * Creates a cancelled result with {@link CancelReason#GUI_EXIT}.
     */
    public static ScreenResult guiExit() {
        return cancel(CancelReason.GUI_EXIT);
    }

    /**
     * Creates a cancelled result with {@link CancelReason#BLANK_INPUT}.
     */
    public static ScreenResult blankInput() {
        return cancel(CancelReason.BLANK_INPUT);
    }

    /**
     * Creates a cancelled result with {@link CancelReason#ERROR}.
     */
    public static ScreenResult error() {
        return cancel(CancelReason.ERROR);
    }
}
