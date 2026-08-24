package dev.cyr1en.promptpaper.execution.postaction;

import dev.cyr1en.promptpaper.execution.dispatch.DispatchError;
import dev.cyr1en.promptpaper.execution.dispatch.DispatchErrorKind;
import java.util.Objects;

/**
 * Unchecked exception thrown when resolving or compiling a post-action fails.
 */
public class PostActionResolutionException extends RuntimeException {

    private final DispatchError error;

    public PostActionResolutionException(DispatchError error) {
        super(Objects.requireNonNull(error, "error must not be null").detail());
        this.error = error;
    }

    public PostActionResolutionException(DispatchErrorKind kind, String message) {
        this(DispatchError.of(kind, message));
    }

    public PostActionResolutionException(DispatchErrorKind kind, String message, Throwable cause) {
        this(DispatchError.of(kind, cause));
    }

    public DispatchError getError() {
        return error;
    }
}
