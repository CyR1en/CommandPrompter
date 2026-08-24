package dev.cyr1en.promptpaper.screen.confirmation;

import dev.cyr1en.promptcore.CancelReason;
import java.util.Objects;

/**
 * Result outcome of a confirmation prompt interaction.
 *
 * <p>Represents one of three terminal outcomes:
 * <ul>
 *   <li>{@link Confirmed} — player explicitly confirmed</li>
 *   <li>{@link Declined} — player explicitly declined or closed the view</li>
 *   <li>{@link Cancelled} — session/prompt was cancelled externally with a {@link CancelReason}</li>
 * </ul>
 */
public sealed interface ConfirmationOutcome {

    record Confirmed() implements ConfirmationOutcome {}

    record Declined() implements ConfirmationOutcome {}

    record Cancelled(CancelReason reason) implements ConfirmationOutcome {
        public Cancelled {
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    static ConfirmationOutcome confirmed() {
        return new Confirmed();
    }

    static ConfirmationOutcome declined() {
        return new Declined();
    }

    static ConfirmationOutcome cancelled(CancelReason reason) {
        return new Cancelled(reason);
    }

    default boolean isConfirmed() {
        return this instanceof Confirmed;
    }

    default boolean isDeclined() {
        return this instanceof Declined;
    }

    default boolean isCancelled() {
        return this instanceof Cancelled;
    }
}
