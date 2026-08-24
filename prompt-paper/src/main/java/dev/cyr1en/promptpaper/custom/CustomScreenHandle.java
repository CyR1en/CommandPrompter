package dev.cyr1en.promptpaper.custom;

import dev.cyr1en.promptui.api.PromptScreenFactory;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Public handle / provider token exposing metadata and state for a registered custom screen.
 *
 * <p>Does not leak mutable registry internal state or direct Plugin references to adapter consumers.</p>
 */
public interface CustomScreenHandle {

    /**
     * Monotonic unique provider / registration ID assigned at registration time.
     */
    long providerId();

    /**
     * Canonical registered screen key.
     */
    String key();

    /**
     * Sanitized cached owner plugin name.
     */
    String ownerName();

    /**
     * Current lifecycle state of the provider.
     */
    ProviderState state();

    /**
     * Returns whether the provider is currently active and eligible to handle prompts.
     */
    default boolean isActive() {
        return state() == ProviderState.ACTIVE;
    }

    /**
     * Returns the screen factory, or {@code null} if detached / inactive.
     *
     * @deprecated Do not invoke the factory outside of the lifecycle gate. Use {@link #invokeFactory} instead.
     */
    @Deprecated
    default PromptScreenFactory factory() {
        return null;
    }

    /**
     * Executes the given action under the provider's lifecycle gate if and only if
     * the provider is currently in the {@link ProviderState#ACTIVE} state.
     *
     * @param action the action to execute
     * @return {@code true} if the action was executed, {@code false} if the provider was not active
     */
    default boolean runIfActive(Runnable action) {
        if (!isActive()) {
            return false;
        }
        action.run();
        return true;
    }

    /**
     * Computes a value from the given supplier under the provider's lifecycle gate if and only if
     * the provider is currently in the {@link ProviderState#ACTIVE} state.
     *
     * @param action the supplier to compute
     * @param <T> the result type
     * @return an optional containing the result if active and non-null, or empty otherwise
     */
    default <T> Optional<T> callIfActive(Supplier<T> action) {
        if (!isActive()) {
            return Optional.empty();
        }
        return Optional.ofNullable(action.get());
    }

    /**
     * Invokes the screen factory under the provider's lifecycle gate if and only if
     * the provider is currently in the {@link ProviderState#ACTIVE} state and has a non-null factory.
     *
     * @param action the factory function to compute
     * @param <T> the result type
     * @return an optional containing the result if active and non-null, or empty otherwise
     */
    default <T> Optional<T> invokeFactory(Function<PromptScreenFactory, T> action) {
        if (!isActive()) {
            return Optional.empty();
        }
        PromptScreenFactory fac = factory();
        if (fac == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(action.apply(fac));
    }
}
