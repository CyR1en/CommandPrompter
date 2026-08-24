package dev.cyr1en.promptpaper.item.snapshot;

import java.util.Objects;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/**
 * Result of an item selection verification check.
 * Encapsulates success/failure state, defensive copy of the verified {@link ItemStack},
 * failure reason, diagnostic error message, and optional exception cause.
 */
public final class VerificationResult {

    private final boolean success;
    private final ItemStack item;
    private final ItemFingerprint fingerprint;
    private final VerificationFailureReason failureReason;
    private final String errorMessage;
    private final Throwable cause;

    private VerificationResult(
            boolean success,
            ItemStack item,
            ItemFingerprint fingerprint,
            VerificationFailureReason failureReason,
            String errorMessage,
            Throwable cause
    ) {
        this.success = success;
        this.item = item != null ? PlayerInventoryAccessor.cloneOrEmpty(item) : null;
        this.fingerprint = fingerprint;
        this.failureReason = failureReason;
        this.errorMessage = errorMessage;
        this.cause = cause;
    }

    /**
     * Creates a successful verification result containing defensive copy of the verified item.
     */
    public static VerificationResult success(ItemStack item, ItemFingerprint fingerprint) {
        Objects.requireNonNull(item, "item cannot be null on success");
        Objects.requireNonNull(fingerprint, "fingerprint cannot be null on success");
        return new VerificationResult(true, item, fingerprint, null, null, null);
    }

    /**
     * Creates a failed verification result with reason and diagnostic message.
     */
    public static VerificationResult failure(VerificationFailureReason reason, String errorMessage) {
        Objects.requireNonNull(reason, "failureReason cannot be null on failure");
        return new VerificationResult(false, null, null, reason, errorMessage, null);
    }

    /**
     * Creates a failed verification result with reason, diagnostic message, and actual item fingerprint observed.
     */
    public static VerificationResult failure(VerificationFailureReason reason, String errorMessage, ItemFingerprint actualFingerprint) {
        Objects.requireNonNull(reason, "failureReason cannot be null on failure");
        return new VerificationResult(false, null, actualFingerprint, reason, errorMessage, null);
    }

    /**
     * Creates a failed verification result with reason, diagnostic message, and underlying throwable cause.
     */
    public static VerificationResult failure(VerificationFailureReason reason, String errorMessage, Throwable cause) {
        Objects.requireNonNull(reason, "failureReason cannot be null on failure");
        return new VerificationResult(false, null, null, reason, errorMessage, cause);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }

    /**
     * Returns a defensive copy of the verified item if verification succeeded.
     */
    public Optional<ItemStack> getItem() {
        return item != null ? Optional.of(item.clone()) : Optional.empty();
    }

    /**
     * Returns a defensive copy of the verified item, or throws {@link IllegalStateException} if verification failed.
     */
    public ItemStack getItemOrThrow() {
        if (!success || item == null) {
            throw new IllegalStateException("Cannot retrieve item from failed verification result: " + errorMessage);
        }
        return item.clone();
    }

    public Optional<ItemFingerprint> getFingerprint() {
        return Optional.ofNullable(fingerprint);
    }

    public Optional<VerificationFailureReason> getFailureReason() {
        return Optional.ofNullable(failureReason);
    }

    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    public Optional<Throwable> getCause() {
        return Optional.ofNullable(cause);
    }

    @Override
    public String toString() {
        if (success) {
            return "VerificationResult[SUCCESS, item=" + item + ", fingerprint=" + fingerprint + "]";
        } else {
            return "VerificationResult[FAILURE, reason=" + failureReason + ", message=" + errorMessage + "]";
        }
    }
}
