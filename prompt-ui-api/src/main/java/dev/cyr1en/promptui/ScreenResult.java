package dev.cyr1en.promptui;

import java.util.Objects;

/**
 * The result of an {@link InputScreen} interaction.
 *
 * <p>For successful input, use {@link #answer(String)}; for cancellation, use
 * {@link #cancel()}. Consumers should check {@link #cancelled()} rather than
 * relying on {@link #answer()} being null. The cancel result carries an empty
 * answer string by convention, not null.
 */
public record ScreenResult(String answer, boolean cancelled) {

    public ScreenResult {
        Objects.requireNonNull(answer, "answer");
    }

    /**
     * Creates a successful result with the given answer.
     */
    public static ScreenResult answer(String answer) {
        return new ScreenResult(answer, false);
    }

    /**
     * Creates a cancelled result with an empty answer string.
     */
    public static ScreenResult cancel() {
        return new ScreenResult("", true);
    }
}
