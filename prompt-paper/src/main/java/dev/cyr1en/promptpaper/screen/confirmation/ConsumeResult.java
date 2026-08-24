package dev.cyr1en.promptpaper.screen.confirmation;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of an atomic nonce consume attempt.
 */
public sealed interface ConsumeResult {

    record Success(ConfirmationBinding binding, ConfirmationDecision decision) implements ConsumeResult {
        public Success {
            Objects.requireNonNull(binding, "binding must not be null");
            Objects.requireNonNull(decision, "decision must not be null");
        }
    }

    record Rejected(RejectionReason reason) implements ConsumeResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    static ConsumeResult success(ConfirmationBinding binding, ConfirmationDecision decision) {
        return new Success(binding, decision);
    }

    static ConsumeResult rejected(RejectionReason reason) {
        return new Rejected(reason);
    }

    default boolean isSuccess() {
        return this instanceof Success;
    }

    default Optional<ConfirmationBinding> optBinding() {
        return this instanceof Success s ? Optional.of(s.binding()) : Optional.empty();
    }

    default Optional<ConfirmationDecision> optDecision() {
        return this instanceof Success s ? Optional.of(s.decision()) : Optional.empty();
    }

    default Optional<RejectionReason> optRejectionReason() {
        return this instanceof Rejected r ? Optional.of(r.reason()) : Optional.empty();
    }
}
