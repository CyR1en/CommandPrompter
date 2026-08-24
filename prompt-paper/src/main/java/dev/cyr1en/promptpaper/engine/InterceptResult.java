package dev.cyr1en.promptpaper.engine;

import dev.cyr1en.promptcore.ParsedCommand;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed outcome of attempting to intercept a command line for prompt execution.
 */
public sealed interface InterceptResult {

    /**
     * A prompt session was successfully initialized and started for the player.
     *
     * @param parsedCommand immutable parsed command structure containing prompt tags and metadata
     */
    record Started(ParsedCommand parsedCommand) implements InterceptResult {
        public Started {
            Objects.requireNonNull(parsedCommand, "parsedCommand");
        }
    }

    /**
     * The command line contains no prompt tags.
     */
    record NoPrompts() implements InterceptResult {
        public static final NoPrompts INSTANCE = new NoPrompts();
    }

    /**
     * The command was rejected under the fail-closed policy (e.g. unknown screen key,
     * missing preset ID, missing validator alias, structural syntax error, or reload in progress).
     *
     * @param reason diagnostic description of why the command was rejected
     */
    record RejectedFailClosed(String reason) implements InterceptResult {
        public RejectedFailClosed {
            reason = reason != null ? reason : "";
        }
        public static final RejectedFailClosed GENERIC = new RejectedFailClosed("");
    }

    /**
     * The command was rejected because the player lacks the required prompt permission.
     */
    record RejectedPermission() implements InterceptResult {
        public static final RejectedPermission INSTANCE = new RejectedPermission();
    }

    /**
     * The command was rejected because the player already has an active prompt session.
     */
    record RejectedActiveSession() implements InterceptResult {
        public static final RejectedActiveSession INSTANCE = new RejectedActiveSession();
    }

    default boolean isStarted() {
        return this instanceof Started;
    }

    default boolean isNoPrompts() {
        return this instanceof NoPrompts;
    }

    default boolean isRejectedFailClosed() {
        return this instanceof RejectedFailClosed;
    }

    default boolean isRejectedPermission() {
        return this instanceof RejectedPermission;
    }

    default boolean isRejectedActiveSession() {
        return this instanceof RejectedActiveSession;
    }

    default Optional<ParsedCommand> toOptional() {
        return this instanceof Started started
                ? Optional.of(started.parsedCommand())
                : Optional.empty();
    }
}
