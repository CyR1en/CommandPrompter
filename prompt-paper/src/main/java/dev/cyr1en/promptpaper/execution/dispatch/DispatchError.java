package dev.cyr1en.promptpaper.execution.dispatch;

import java.util.Objects;

/**
 * Immutable failure descriptor for command dispatch operations.
 * Detail messages are bounded and C0-safe, and do not include full command strings.
 */
public record DispatchError(
        DispatchErrorKind kind,
        String detail,
        Throwable cause
) {
    public DispatchError {
        Objects.requireNonNull(kind, "kind must not be null");
        detail = DispatchSanitizer.sanitizeDetail(detail);
    }

    public static DispatchError of(DispatchErrorKind kind, String detail) {
        return new DispatchError(kind, detail, null);
    }

    public static DispatchError of(DispatchErrorKind kind, String detail, Throwable cause) {
        return new DispatchError(kind, detail, cause);
    }

    public static DispatchError of(DispatchErrorKind kind, Throwable cause) {
        String msg = cause != null
                ? cause.getClass().getSimpleName() + ": " + (cause.getMessage() != null ? cause.getMessage() : "no detail")
                : "unknown exception";
        return new DispatchError(kind, msg, cause);
    }
}
